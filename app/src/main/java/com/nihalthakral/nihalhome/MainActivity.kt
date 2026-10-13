package com.nihalthakral.nihalhome

import android.annotation.SuppressLint
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.ComponentActivity
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle
import java.io.ByteArrayInputStream

class MainActivity : ComponentActivity() {

    private lateinit var webView: WebView
    private lateinit var rewardedAds: RewardedAdManager
    private lateinit var interstitialAds: InterstitialAdManager
    private var pendingAdResult: String? = null
    private var fallbackAdShowing = false

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

        webView.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(
                view: WebView,
                request: WebResourceRequest
            ): Boolean = !request.url.toString().startsWith(GAME_URL)

            override fun shouldInterceptRequest(
                view: WebView,
                request: WebResourceRequest
            ): WebResourceResponse? {
                val scheme = request.url.scheme
                if (scheme == "http" || scheme == "https") {
                    return WebResourceResponse("text/plain", "utf-8", ByteArrayInputStream(ByteArray(0)))
                }
                return null
            }
        }
        webView.webChromeClient = WebChromeClient()

        rewardedAds = RewardedAdManager(this, object : RewardedAdManager.Listener {
            override fun onRewardEarned() = postAdResult(RESULT_EARNED)
            override fun onNoReward(reason: String) = postAdResult(reason)
            override fun onFallbackAdVisibility(showing: Boolean) {
                fallbackAdShowing = showing
                if (showing) webView.onPause() else webView.onResume()
            }
        })
        interstitialAds = InterstitialAdManager(this) { showing ->
            fallbackAdShowing = showing
            if (showing) webView.onPause() else webView.onResume()
        }
        interstitialAds.preload()
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
        if (!fallbackAdShowing) webView.onResume()
        flushAdResult()
    }

    override fun onPause() {
        webView.onPause()
        super.onPause()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) hideSystemBars()
    }

    override fun onDestroy() {
        interstitialAds.destroy()
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

        @JavascriptInterface
        fun preloadInterstitialAd() {
            runOnUiThread { interstitialAds.preload() }
        }

        @JavascriptInterface
        fun showInterstitialAd(): Boolean = interstitialAds.requestShow()

        @JavascriptInterface
        fun getCooldownMs(): Long = rewardedAds.remainingCooldownMs()
    }

    companion object {
        private const val GAME_URL = "file:///android_asset/game.html"
        private const val RESULT_EARNED = "earned"
    }
}
