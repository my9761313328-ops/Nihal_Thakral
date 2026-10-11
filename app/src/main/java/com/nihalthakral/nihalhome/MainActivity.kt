package com.nihalthakral.nihalhome

import android.annotation.SuppressLint
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.ComponentActivity
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle

class MainActivity : ComponentActivity() {

    private lateinit var webView: WebView
    private lateinit var rewardedAds: RewardedAdManager
    private var pendingAdResult: String? = null

    @SuppressLint("SetJavaScriptEnabled", "ClickableViewAccessibility")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            window.attributes.layoutInDisplayCutoutMode =
                android.view.WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        }
        setContentView(R.layout.activity_main)
        hideSystemBars()

        webView = findViewById(R.id.gameWebView)
        webView.setBackgroundColor(Color.BLACK)
        webView.overScrollMode = View.OVER_SCROLL_NEVER
        webView.isVerticalScrollBarEnabled = false
        webView.isHorizontalScrollBarEnabled = false
        webView.isLongClickable = false
        webView.isHapticFeedbackEnabled = false
        webView.setOnLongClickListener { true }

        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            mediaPlaybackRequiresUserGesture = false
            allowFileAccess = true
            allowContentAccess = false
            setSupportZoom(false)
            builtInZoomControls = false
            displayZoomControls = false
        }

        webView.webViewClient = WebViewClient()
        webView.webChromeClient = WebChromeClient()

        rewardedAds = RewardedAdManager(this, object : RewardedAdManager.Listener {
            override fun onRewardEarned() = postAdResult(RESULT_EARNED)
            override fun onNoReward(reason: String) = postAdResult(reason)
        })
        webView.addJavascriptInterface(AdBridge(), "AndroidAds")

        if (savedInstanceState == null) {
            webView.loadUrl(GAME_URL)
        } else {
            webView.restoreState(savedInstanceState)
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        webView.saveState(outState)
    }

    override fun onResume() {
        super.onResume()
        webView.onResume()
        webView.resumeTimers()
        flushAdResult()
    }

    override fun onPause() {
        webView.onPause()
        if (!rewardedAds.isBusy) webView.pauseTimers()
        super.onPause()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) hideSystemBars()
    }

    override fun onDestroy() {
        (webView.parent as? ViewGroup)?.removeView(webView)
        webView.stopLoading()
        webView.destroy()
        super.onDestroy()
    }

    private fun hideSystemBars() {
        val controller = WindowInsetsControllerCompat(window, window.decorView)
        controller.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        controller.hide(WindowInsetsCompat.Type.systemBars())
    }

    private fun postAdResult(result: String) {
        runOnUiThread {
            pendingAdResult = result
            if (lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) flushAdResult()
        }
    }

    private fun flushAdResult() {
        val result = pendingAdResult ?: return
        pendingAdResult = null
        webView.evaluateJavascript(
            "window.onRewardedAdResult && window.onRewardedAdResult('$result')",
            null
        )
    }

    private inner class AdBridge {
        @JavascriptInterface
        fun showRewardedAd() {
            runOnUiThread { rewardedAds.loadAndShow() }
        }
    }

    companion object {
        private const val GAME_URL = "file:///android_asset/game.html"
        private const val RESULT_EARNED = "earned"
    }
}
