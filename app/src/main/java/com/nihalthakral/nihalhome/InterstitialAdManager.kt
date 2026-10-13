package com.nihalthakral.nihalhome

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Handler
import android.os.Looper
import android.os.Process
import androidx.activity.ComponentActivity
import com.google.android.libraries.ads.mobile.sdk.common.AdLoadCallback
import com.google.android.libraries.ads.mobile.sdk.common.AdRequest
import com.google.android.libraries.ads.mobile.sdk.common.FullScreenContentError
import com.google.android.libraries.ads.mobile.sdk.common.LoadAdError
import com.google.android.libraries.ads.mobile.sdk.interstitial.InterstitialAd
import com.google.android.libraries.ads.mobile.sdk.interstitial.InterstitialAdEventCallback

class InterstitialAdManager(
    private val activity: ComponentActivity,
    private val onFallbackVisibility: (Boolean) -> Unit
) {

    private val mainHandler = Handler(Looper.getMainLooper())

    private var ad: InterstitialAd? = null
    private var fallbackShowing = false

    @Volatile
    private var loading = false

    @Volatile
    private var destroyed = false

    @Volatile
    private var loadToken = 0

    @Volatile
    private var ready = false

    @Volatile
    private var fallbackReady = false

    private val timeoutRunnable = Runnable {
        if (loading) {
            loading = false
            loadToken++
        }
    }

    fun preload() {
        if (destroyed || loading || ad != null) return
        if (!isOnline()) return
        loading = true
        fallbackReady = false
        val token = ++loadToken
        mainHandler.removeCallbacks(timeoutRunnable)
        mainHandler.postDelayed(timeoutRunnable, LOAD_TIMEOUT_MS)
        Thread {
            try {
                Process.setThreadPriority(Process.THREAD_PRIORITY_BACKGROUND)
                AdsInitializer.ensure(activity)
                request(token, true)
            } catch (e: Exception) {
                mainHandler.post { finishLoad(token) }
            }
        }.start()
    }

    fun requestShow(): Boolean {
        if (destroyed) return false
        if (ready) {
            mainHandler.post { present() }
            return true
        }
        if (fallbackReady && !fallbackShowing && isOnline()) {
            fallbackReady = false
            mainHandler.post { presentFallback() }
            return true
        }
        mainHandler.post { preload() }
        return false
    }

    fun destroy() {
        destroyed = true
        ready = false
        fallbackReady = false
        ad = null
        loading = false
        mainHandler.removeCallbacksAndMessages(null)
    }

    private fun finishLoad(token: Int) {
        if (token != loadToken) return
        loading = false
        mainHandler.removeCallbacks(timeoutRunnable)
    }

    private fun request(token: Int, allowRetryOnMain: Boolean) {
        if (destroyed || token != loadToken || !loading) return
        try {
            InterstitialAd.load(
                AdRequest.Builder(AD_UNIT_ID).build(),
                object : AdLoadCallback<InterstitialAd> {
                    override fun onAdLoaded(ad: InterstitialAd) {
                        mainHandler.post {
                            if (destroyed || token != loadToken) return@post
                            finishLoad(token)
                            this@InterstitialAdManager.ad = ad
                            ready = true
                        }
                    }

                    override fun onAdFailedToLoad(adError: LoadAdError) {
                        mainHandler.post {
                            if (destroyed || token != loadToken) return@post
                            finishLoad(token)
                            if (isNoFill(adError)) fallbackReady = true
                        }
                    }
                }
            )
        } catch (e: Exception) {
            if (allowRetryOnMain) {
                mainHandler.post { request(token, false) }
            } else {
                mainHandler.post { finishLoad(token) }
            }
        }
    }

    private fun isNoFill(adError: LoadAdError): Boolean {
        val label = adError.code.toString().uppercase().replace("_", "")
        return label.contains("NOFILL")
    }

    private fun present() {
        val current = ad ?: return
        ad = null
        ready = false
        if (destroyed || activity.isFinishing || activity.isDestroyed) return
        current.adEventCallback = object : InterstitialAdEventCallback {
            override fun onAdDismissedFullScreenContent() {
                mainHandler.post { preload() }
            }

            override fun onAdFailedToShowFullScreenContent(
                fullScreenContentError: FullScreenContentError
            ) {
                mainHandler.post { preload() }
            }
        }
        try {
            current.show(activity)
        } catch (e: Exception) {
            preload()
        }
    }

    private fun presentFallback() {
        if (destroyed || fallbackShowing || activity.isFinishing || activity.isDestroyed) return
        fallbackShowing = true
        onFallbackVisibility(true)
        try {
            FallbackAdPlayer(activity, false) {
                fallbackShowing = false
                onFallbackVisibility(false)
                mainHandler.post { preload() }
            }.show()
        } catch (e: Exception) {
            fallbackShowing = false
            onFallbackVisibility(false)
        }
    }

    private fun isOnline(): Boolean {
        val manager = activity.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            ?: return false
        val network = manager.activeNetwork ?: return false
        val caps = manager.getNetworkCapabilities(network) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    companion object {
        private const val AD_UNIT_ID = "ca-app-pub-3940256099942544/1033173712"
        private const val LOAD_TIMEOUT_MS = 15000L
    }
}
