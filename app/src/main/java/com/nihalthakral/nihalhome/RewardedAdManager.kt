package com.nihalthakral.nihalhome

import android.app.Activity
import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Handler
import android.os.Looper
import com.google.android.libraries.ads.mobile.sdk.MobileAds
import com.google.android.libraries.ads.mobile.sdk.common.AdLoadCallback
import com.google.android.libraries.ads.mobile.sdk.common.AdRequest
import com.google.android.libraries.ads.mobile.sdk.common.FullScreenContentError
import com.google.android.libraries.ads.mobile.sdk.common.LoadAdError
import com.google.android.libraries.ads.mobile.sdk.initialization.InitializationConfig
import com.google.android.libraries.ads.mobile.sdk.rewarded.RewardedAd
import com.google.android.libraries.ads.mobile.sdk.rewarded.RewardedAdEventCallback

class RewardedAdManager(
    private val activity: Activity,
    private val listener: Listener
) {

    interface Listener {
        fun onRewardEarned()
        fun onNoReward(reason: String)
    }

    private val mainHandler = Handler(Looper.getMainLooper())

    private var busy = false
    private var attempt = 0
    @Volatile
    private var earned = false

    fun loadAndShow() {
        if (busy) return
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
                ensureInitialized()
                mainHandler.post { requestAd(token) }
            } catch (e: Exception) {
                mainHandler.post { failAttempt(token) }
            }
        }.start()
    }

    private fun requestAd(token: Int) {
        if (token != attempt || !busy) return
        try {
            RewardedAd.load(
                AdRequest.Builder(AD_UNIT_ID).build(),
                object : AdLoadCallback<RewardedAd> {
                    override fun onAdLoaded(ad: RewardedAd) {
                        mainHandler.post { present(token, ad) }
                    }

                    override fun onAdFailedToLoad(adError: LoadAdError) {
                        mainHandler.post { failAttempt(token) }
                    }
                }
            )
        } catch (e: Exception) {
            failAttempt(token)
        }
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

    private fun ensureInitialized() {
        synchronized(LOCK) {
            if (initialized) return
            MobileAds.initialize(
                activity,
                InitializationConfig.Builder(APP_ID).build()
            ) {}
            initialized = true
        }
    }

    companion object {
        private const val APP_ID = "ca-app-pub-5939817111566865~6465125511"
        private const val AD_UNIT_ID = "ca-app-pub-5939817111566865/1834673417"
        private const val LOAD_TIMEOUT_MS = 15000L
        const val REASON_DISMISSED = "dismissed"
        const val REASON_FAILED = "failed"

        private val LOCK = Any()
        private var initialized = false
    }
}
