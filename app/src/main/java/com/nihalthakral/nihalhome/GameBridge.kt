package com.nihalthakral.nihalhome

import android.webkit.JavascriptInterface

class GameBridge(private val adManager: AdManager) {

    @JavascriptInterface
    fun onGameOver() {
        adManager.onGameOver()
    }
}
