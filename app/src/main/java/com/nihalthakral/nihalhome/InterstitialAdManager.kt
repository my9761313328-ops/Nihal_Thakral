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

class InterstitialAdManager(private val activity: ComponentActivity) {

    private val mainHandler = Handler(Looper.getMainLooper())

    private var ad: InterstitialAd? = null
    private var loading = false
    private var destroyed = false

    @Volatile
    private var ready = false

    fun preload() {
        if (destroyed || loading || ad != null) return
        if (!isOnline()) return
        loading = true
        Thread {
            try {
                AdsInitializer.ensure(activity)
                mainHandler.post { request() }
            } catch (e: Exception) {
                mainHandler.post { loading = false }
            }
        }.start()
    }

    fun requestShow(): Boolean {
        if (destroyed) return false
        if (!ready) {
            mainHandler.post { preload() }
            return false
        }
        mainHandler.post { present() }
        return true
    }

    fun destroy() {
        destroyed = true
        ready = false
        ad = null
        loading = false
        mainHandler.removeCallbacksAndMessages(null)
    }

    private fun request() {
        if (destroyed) {
            loading = false
            return
        }
        try {
            InterstitialAd.load(
                AdRequest.Builder(AD_UNIT_ID).build(),
                object : AdLoadCallback<InterstitialAd> {
                    override fun onAdLoaded(ad: InterstitialAd) {
                        mainHandler.post {
                            loading = false
                            if (destroyed) return@post
                            this@InterstitialAdManager.ad = ad
                            ready = true
                        }
                    }

                    override fun onAdFailedToLoad(adError: LoadAdError) {
                        mainHandler.post { loading = false }
                    }
                }
            )
        } catch (e: Exception) {
            loading = false
        }
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

    private fun isOnline(): Boolean {
        val manager = activity.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            ?: return false
        val network = manager.activeNetwork ?: return false
        val caps = manager.getNetworkCapabilities(network) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    companion object {
        private const val AD_UNIT_ID = "ca-app-pub-3940256099942544/1033173712"
    }
}
