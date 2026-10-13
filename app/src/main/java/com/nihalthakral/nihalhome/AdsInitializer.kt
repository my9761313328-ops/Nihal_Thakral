package com.nihalthakral.nihalhome

import android.content.Context
import com.google.android.libraries.ads.mobile.sdk.MobileAds
import com.google.android.libraries.ads.mobile.sdk.initialization.InitializationConfig

object AdsInitializer {

    private const val APP_ID = "ca-app-pub-5939817111566865~6465125511"

    private val lock = Any()
    private var initialized = false

    fun ensure(context: Context) {
        synchronized(lock) {
            if (initialized) return
            MobileAds.initialize(
                context,
                InitializationConfig.Builder(APP_ID).build()
            ) {}
            initialized = true
        }
    }
}
