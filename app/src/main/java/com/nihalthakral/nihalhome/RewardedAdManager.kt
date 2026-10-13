package com.nihalthakral.nihalhome

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Handler
import android.os.Looper
import androidx.activity.ComponentActivity
import com.google.android.libraries.ads.mobile.sdk.common.AdLoadCallback
import com.google.android.libraries.ads.mobile.sdk.common.AdRequest
import com.google.android.libraries.ads.mobile.sdk.common.FullScreenContentError
import com.google.android.libraries.ads.mobile.sdk.common.LoadAdError
import com.google.android.libraries.ads.mobile.sdk.rewarded.RewardedAd
import com.google.android.libraries.ads.mobile.sdk.rewarded.RewardedAdEventCallback

class RewardedAdManager(
    private val activity: ComponentActivity,
    private val listener: Listener
) {

    interface Listener {
        fun onRewardEarned()
        fun onNoReward(reason: String)
        fun onFallbackAdVisibility(showing: Boolean) {}
    }

    private val mainHandler = Handler(Looper.getMainLooper())
    private val prefs = activity.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private var busy = false
    private var attempt = 0
    @Volatile
    private var earned = false

    fun remainingCooldownMs(): Long {
        val last = prefs.getLong(KEY_LAST_REQUEST, 0L)
        if (last == 0L) return 0L
        val now = System.currentTimeMillis()
        val elapsed = now - last
        if (elapsed < 0) {
            prefs.edit().putLong(KEY_LAST_REQUEST, now).apply()
            return COOLDOWN_MS
        }
        return (COOLDOWN_MS - elapsed).coerceAtLeast(0L)
    }

    fun loadAndShow() {
        if (busy) return
        if (remainingCooldownMs() > 0L) {
            listener.onNoReward(REASON_COOLDOWN)
            return
        }
        if (!isOnline()) {
            listener.onNoReward(REASON_FAILED)
            return
        }
        busy = true
        earned = false
        val token = ++attempt
        mainHandler.postDelayed({ failAttempt(token) }, LOAD_TIMEOUT_MS)

        Thread {
            try {
                AdsInitializer.ensure(activity)
                mainHandler.post { requestAd(token) }
            } catch (e: Exception) {
                mainHandler.post { failAttempt(token) }
            }
        }.start()
    }

    private fun requestAd(token: Int) {
        if (token != attempt || !busy) return
        val previousStamp = prefs.getLong(KEY_LAST_REQUEST, 0L)
        prefs.edit().putLong(KEY_LAST_REQUEST, System.currentTimeMillis()).apply()
        try {
            RewardedAd.load(
                AdRequest.Builder(AD_UNIT_ID).build(),
                object : AdLoadCallback<RewardedAd> {
                    override fun onAdLoaded(ad: RewardedAd) {
                        mainHandler.post { present(token, ad) }
                    }

                    override fun onAdFailedToLoad(adError: LoadAdError) {
                        mainHandler.post {
                            if (token == attempt && busy && isNoFill(adError)) {
                                showFallbackAd()
                                return@post
                            }
                            if (token == attempt && !requestReachedGoogle(adError)) {
                                restoreStamp(previousStamp)
                            }
                            failAttempt(token)
                        }
                    }
                }
            )
        } catch (e: Exception) {
            restoreStamp(previousStamp)
            failAttempt(token)
        }
    }

    private fun isNoFill(adError: LoadAdError): Boolean {
        val label = adError.code.toString().uppercase().replace("_", "")
        return label.contains("NOFILL")
    }

    private fun showFallbackAd() {
        mainHandler.removeCallbacksAndMessages(null)
        if (activity.isFinishing || activity.isDestroyed) {
            busy = false
            return
        }
        listener.onFallbackAdVisibility(true)
        try {
            FallbackAdPlayer(activity) { completed ->
                listener.onFallbackAdVisibility(false)
                mainHandler.post { finishAttempt(if (completed) null else REASON_FAILED) }
            }.show()
        } catch (e: Exception) {
            listener.onFallbackAdVisibility(false)
            finishAttempt(REASON_FAILED)
        }
    }

    private fun requestReachedGoogle(adError: LoadAdError): Boolean {
        val label = adError.code.toString().uppercase()
        return !(label.contains("NETWORK") || label.contains("INTERNAL"))
    }

    private fun restoreStamp(previous: Long) {
        prefs.edit().putLong(KEY_LAST_REQUEST, previous).apply()
    }

    private fun present(token: Int, ad: RewardedAd) {
        if (token != attempt || !busy) return
        mainHandler.removeCallbacksAndMessages(null)
        if (activity.isFinishing || activity.isDestroyed) {
            busy = false
            return
        }
        ad.adEventCallback = object : RewardedAdEventCallback {
            override fun onAdDismissedFullScreenContent() {
                mainHandler.post { finishAttempt(if (earned) null else REASON_DISMISSED) }
            }

            override fun onAdFailedToShowFullScreenContent(
                fullScreenContentError: FullScreenContentError
            ) {
                mainHandler.post { finishAttempt(REASON_FAILED) }
            }
        }
        try {
            ad.show(activity) { earned = true }
        } catch (e: Exception) {
            finishAttempt(REASON_FAILED)
        }
    }

    private fun failAttempt(token: Int) {
        if (token != attempt || !busy) return
        attempt++
        mainHandler.removeCallbacksAndMessages(null)
        finishAttempt(REASON_FAILED)
    }

    private fun finishAttempt(failureReason: String?) {
        if (!busy) return
        busy = false
        if (failureReason == null) listener.onRewardEarned() else listener.onNoReward(failureReason)
    }

    private fun isOnline(): Boolean {
        val manager = activity.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            ?: return false
        val network = manager.activeNetwork ?: return false
        val caps = manager.getNetworkCapabilities(network) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    companion object {
        private const val AD_UNIT_ID = "ca-app-pub-5939817111566865/1834673417"
        private const val LOAD_TIMEOUT_MS = 15000L
        private const val COOLDOWN_MS = 65000L
        private const val PREFS_NAME = "rewarded_ad_prefs"
        private const val KEY_LAST_REQUEST = "last_request_at"
        const val REASON_COOLDOWN = "cooldown"
        const val REASON_DISMISSED = "dismissed"
        const val REASON_FAILED = "failed"
    }
}
