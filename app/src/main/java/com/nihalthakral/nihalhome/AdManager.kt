package com.nihalthakral.nihalhome

import android.app.Activity
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import com.google.android.libraries.ads.mobile.sdk.MobileAds
import com.google.android.libraries.ads.mobile.sdk.common.AdLoadCallback
import com.google.android.libraries.ads.mobile.sdk.common.AdRequest
import com.google.android.libraries.ads.mobile.sdk.common.FullScreenContentError
import com.google.android.libraries.ads.mobile.sdk.common.LoadAdError
import com.google.android.libraries.ads.mobile.sdk.initialization.InitializationConfig
import com.google.android.libraries.ads.mobile.sdk.interstitial.InterstitialAd
import com.google.android.libraries.ads.mobile.sdk.interstitial.InterstitialAdEventCallback

class AdManager(activity: Activity) {

    private var activityRef: Activity? = activity
    private val appContext: Context = activity.applicationContext
    private val prefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val handler = Handler(Looper.getMainLooper())
    private val preloadRunnable = Runnable { requestAdIfAllowed() }

    private var interstitialAd: InterstitialAd? = null
    private var loadedAtElapsed = 0L
    private var sdkReady = false
    private var isLoading = false
    private var isShowing = false
    private var gameOverCount = 0

    fun initialize() {
        Thread {
            MobileAds.initialize(appContext, InitializationConfig.Builder(APP_ID).build()) {
                handler.post {
                    if (activityRef == null) return@post
                    sdkReady = true
                    requestAdIfAllowed()
                }
            }
        }.start()
    }

    fun onGameOver() {
        handler.post {
            if (activityRef == null) return@post
            gameOverCount++
            if (gameOverCount > GAME_OVERS_BEFORE_AD) showIfReady()
        }
    }

    fun destroy() {
        handler.removeCallbacksAndMessages(null)
        interstitialAd = null
        activityRef = null
    }

    private fun showIfReady() {
        val act = activityRef ?: return
        if (isShowing) return
        val ad = interstitialAd
        if (ad == null || isExpired()) {
            interstitialAd = null
            requestAdIfAllowed()
            return
        }
        interstitialAd = null
        isShowing = true
        ad.adEventCallback = object : InterstitialAdEventCallback {
            override fun onAdShowedFullScreenContent() {
                gameOverCount = 0
            }

            override fun onAdDismissedFullScreenContent() {
                isShowing = false
                schedulePreload()
            }

            override fun onAdFailedToShowFullScreenContent(fullScreenContentError: FullScreenContentError) {
                Log.e(TAG, "Interstitial failed to show: ${fullScreenContentError.message}")
                isShowing = false
                schedulePreload()
            }
        }
        ad.show(act)
    }

    private fun schedulePreload() {
        handler.removeCallbacks(preloadRunnable)
        handler.postDelayed(preloadRunnable, remainingCapMs())
    }

    private fun requestAdIfAllowed() {
        if (!sdkReady || isLoading || isShowing || activityRef == null) return
        if (interstitialAd != null && !isExpired()) return
        interstitialAd = null

        val wait = remainingCapMs()
        if (wait > 0L) {
            handler.removeCallbacks(preloadRunnable)
            handler.postDelayed(preloadRunnable, wait)
            return
        }

        isLoading = true
        prefs.edit().putLong(KEY_LAST_REQUEST, System.currentTimeMillis()).apply()

        InterstitialAd.load(
            AdRequest.Builder(AD_UNIT_ID).build(),
            object : AdLoadCallback<InterstitialAd> {
                override fun onAdLoaded(ad: InterstitialAd) {
                    handler.post {
                        isLoading = false
                        if (activityRef == null) return@post
                        interstitialAd = ad
                        loadedAtElapsed = SystemClock.elapsedRealtime()
                    }
                }

                override fun onAdFailedToLoad(adError: LoadAdError) {
                    Log.e(TAG, "Interstitial failed to load: ${adError.message}")
                    handler.post {
                        isLoading = false
                        interstitialAd = null
                    }
                }
            }
        )
    }

    private fun isExpired(): Boolean =
        SystemClock.elapsedRealtime() - loadedAtElapsed > AD_EXPIRY_MS

    private fun remainingCapMs(): Long {
        val now = System.currentTimeMillis()
        val last = prefs.getLong(KEY_LAST_REQUEST, 0L)
        if (last == 0L) return 0L
        if (now < last) {
            prefs.edit().putLong(KEY_LAST_REQUEST, now).apply()
            return REQUEST_GAP_MS
        }
        return (REQUEST_GAP_MS - (now - last)).coerceAtLeast(0L)
    }

    companion object {
        private const val TAG = "AdManager"
        private const val APP_ID = "ca-app-pub-3940256099942544~3347511713"
        private const val AD_UNIT_ID = "ca-app-pub-3940256099942544/1033173712"
        private const val PREFS_NAME = "ad_prefs"
        private const val KEY_LAST_REQUEST = "last_ad_request_time"
        private const val REQUEST_GAP_MS = 65_000L
        private const val AD_EXPIRY_MS = 55 * 60 * 1000L
        private const val GAME_OVERS_BEFORE_AD = 3
    }
}
