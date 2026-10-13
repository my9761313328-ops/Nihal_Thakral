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
import com.google.android.libraries.ads.mobile.sdk.interstitial.InterstitialAd
import com.google.android.libraries.ads.mobile.sdk.interstitial.InterstitialAdEventCallback

class InterstitialAdManager(
    private val activity: ComponentActivity,
    private val onFallbackVisibility: (Boolean) -> Unit,
    private val onResult: (Boolean) -> Unit
) {

    private val mainHandler = Handler(Looper.getMainLooper())

    private var busy = false
    private var destroyed = false
    private var attempt = 0
    private var timeoutRunnable: Runnable? = null

    fun loadAndShow() {
        if (destroyed || busy) return
        if (!isOnline()) {
            onResult(false)
            return
        }
        busy = true
        val token = ++attempt
        val timeout = Runnable { failAttempt(token) }
        timeoutRunnable = timeout
        mainHandler.postDelayed(timeout, LOAD_TIMEOUT_MS)

        Thread {
            try {
                AdsInitializer.ensure(activity)
                mainHandler.post { request(token) }
            } catch (e: Exception) {
                mainHandler.post { failAttempt(token) }
            }
        }.start()
    }

    fun destroy() {
        destroyed = true
        busy = false
        timeoutRunnable = null
        mainHandler.removeCallbacksAndMessages(null)
    }

    private fun request(token: Int) {
        if (destroyed || token != attempt || !busy) return
        try {
            InterstitialAd.load(
                AdRequest.Builder(AD_UNIT_ID).build(),
                object : AdLoadCallback<InterstitialAd> {
                    override fun onAdLoaded(ad: InterstitialAd) {
                        mainHandler.post { present(token, ad) }
                    }

                    override fun onAdFailedToLoad(adError: LoadAdError) {
                        mainHandler.post {
                            if (token != attempt || !busy) return@post
                            if (isNoFill(adError)) showFallback() else failAttempt(token)
                        }
                    }
                }
            )
        } catch (e: Exception) {
            failAttempt(token)
        }
    }

    private fun isNoFill(adError: LoadAdError): Boolean {
        val label = adError.code.toString().uppercase().replace("_", "")
        return label.contains("NOFILL")
    }

    private fun present(token: Int, ad: InterstitialAd) {
        if (destroyed || token != attempt || !busy) return
        cancelTimeout()
        if (activity.isFinishing || activity.isDestroyed) {
            busy = false
            return
        }
        ad.adEventCallback = object : InterstitialAdEventCallback {
            override fun onAdDismissedFullScreenContent() {
                mainHandler.post { finish(true) }
            }

            override fun onAdFailedToShowFullScreenContent(
                fullScreenContentError: FullScreenContentError
            ) {
                mainHandler.post { finish(false) }
            }
        }
        try {
            ad.show(activity)
        } catch (e: Exception) {
            finish(false)
        }
    }

    private fun showFallback() {
        cancelTimeout()
        if (destroyed || activity.isFinishing || activity.isDestroyed) {
            busy = false
            return
        }
        onFallbackVisibility(true)
        try {
            FallbackAdPlayer(activity, false) {
                onFallbackVisibility(false)
                mainHandler.post { finish(true) }
            }.show()
        } catch (e: Exception) {
            onFallbackVisibility(false)
            finish(false)
        }
    }

    private fun failAttempt(token: Int) {
        if (token != attempt || !busy) return
        attempt++
        cancelTimeout()
        finish(false)
    }

    private fun finish(shown: Boolean) {
        if (!busy) return
        busy = false
        if (!destroyed) onResult(shown)
    }

    private fun cancelTimeout() {
        timeoutRunnable?.let { mainHandler.removeCallbacks(it) }
        timeoutRunnable = null
    }

    private fun isOnline(): Boolean {
        val manager = activity.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            ?: return false
        val network = manager.activeNetwork ?: return false
        val caps = manager.getNetworkCapabilities(network) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    companion object {
        private const val AD_UNIT_ID = "ca-app-pub-5939817111566865/4348884014"
        private const val LOAD_TIMEOUT_MS = 15000L
    }
}
