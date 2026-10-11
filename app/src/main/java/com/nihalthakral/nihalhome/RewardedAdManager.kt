package com.nihalthakral.nihalhome

import android.app.Activity
import com.google.android.libraries.ads.mobile.sdk.MobileAds
import com.google.android.libraries.ads.mobile.sdk.common.AdRequest
import com.google.android.libraries.ads.mobile.sdk.common.FullScreenContentError
import com.google.android.libraries.ads.mobile.sdk.common.PreloadConfiguration
import com.google.android.libraries.ads.mobile.sdk.initialization.InitializationConfig
import com.google.android.libraries.ads.mobile.sdk.rewarded.RewardedAdEventCallback
import com.google.android.libraries.ads.mobile.sdk.rewarded.RewardedAdPreloader

class RewardedAdManager(
    private val activity: Activity,
    private val listener: Listener
) {

    interface Listener {
        fun onRewardEarned()
        fun onNoReward(reason: String)
    }

    @Volatile
    private var showing = false

    @Volatile
    private var earned = false

    fun start() {
        if (started) return
        started = true
        Thread {
            try {
                MobileAds.initialize(
                    activity,
                    InitializationConfig.Builder(APP_ID).build()
                ) {}
                val request = AdRequest.Builder(AD_UNIT_ID).build()
                RewardedAdPreloader.start(AD_UNIT_ID, PreloadConfiguration(request, 1))
                initialized = true
            } catch (e: Exception) {
                started = false
            }
        }.start()
    }

    fun show() {
        if (showing) return
        if (!initialized) {
            listener.onNoReward(REASON_UNAVAILABLE)
            return
        }
        try {
            val ad = RewardedAdPreloader.pollAd(AD_UNIT_ID)
            if (ad == null) {
                listener.onNoReward(REASON_UNAVAILABLE)
                return
            }
            showing = true
            earned = false
            ad.adEventCallback = object : RewardedAdEventCallback {
                override fun onAdDismissedFullScreenContent() {
                    showing = false
                    if (earned) listener.onRewardEarned() else listener.onNoReward(REASON_DISMISSED)
                }

                override fun onAdFailedToShowFullScreenContent(
                    fullScreenContentError: FullScreenContentError
                ) {
                    showing = false
                    listener.onNoReward(REASON_FAILED)
                }
            }
            ad.show(activity) { earned = true }
        } catch (e: Exception) {
            showing = false
            listener.onNoReward(REASON_FAILED)
        }
    }

    companion object {
        private const val APP_ID = "ca-app-pub-3940256099942544~3347511713"
        private const val AD_UNIT_ID = "ca-app-pub-3940256099942544/5224354917"
        const val REASON_UNAVAILABLE = "unavailable"
        const val REASON_DISMISSED = "dismissed"
        const val REASON_FAILED = "failed"

        @Volatile
        private var started = false

        @Volatile
        private var initialized = false
    }
}
