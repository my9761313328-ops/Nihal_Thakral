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
import androidx.activity.OnBackPressedCallback
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
    private var pendingInterstitialResult: String? = null
    private var fallbackAdShowing = false
    private var sheetOpen = false
    private var currentGame = GameCatalog.latestIndex
    private lateinit var switcher: GameSwitcher

    @SuppressLint("SetJavaScriptEnabled", "ClickableViewAccessibility")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            window.attributes.layoutInDisplayCutoutMode =
                android.view.WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        }
        setContentView(R.layout.activity_main)
        window.setBackgroundDrawable(null)
        hideSystemBars()

        webView = findViewById(R.id.gameWebView)
        webView.setBackgroundColor(Color.BLACK)
        webView.overScrollMode = View.OVER_SCROLL_NEVER
        webView.isVerticalScrollBarEnabled = false
        webView.isHorizontalScrollBarEnabled = false
        webView.isLongClickable = false
        webView.isHapticFeedbackEnabled = false
        webView.setOnLongClickListener { true }
        (webView.parent as? View)?.background = null
        webView.setLayerType(View.LAYER_TYPE_HARDWARE, null)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            webView.setRendererPriorityPolicy(WebView.RENDERER_PRIORITY_IMPORTANT, false)
        }

        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            mediaPlaybackRequiresUserGesture = false
            allowFileAccess = true
            allowContentAccess = false
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) safeBrowsingEnabled = false
            setSupportZoom(false)
            builtInZoomControls = false
            displayZoomControls = false
        }

        webView.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(
                view: WebView,
                request: WebResourceRequest
            ): Boolean = !request.url.toString().startsWith(ASSET_PREFIX)

            override fun onPageFinished(view: WebView, url: String?) {
                view.animate().alpha(1f).setDuration(220).start()
            }

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
        interstitialAds = InterstitialAdManager(
            this,
            { showing ->
                fallbackAdShowing = showing
                if (showing) webView.onPause() else webView.onResume()
            },
            { shown -> postInterstitialResult(if (shown) RESULT_SHOWN else RESULT_FAILED) }
        )
        webView.addJavascriptInterface(AdBridge(), "AndroidAds")

        val backCallback = object : OnBackPressedCallback(false) {
            override fun handleOnBackPressed() {
                switcher.close()
            }
        }
        onBackPressedDispatcher.addCallback(this, backCallback)

        switcher = GameSwitcher(
            this,
            GameCatalog.games,
            { index -> switchTo(index) },
            { open ->
                sheetOpen = open
                backCallback.isEnabled = open
                if (open) webView.onPause() else if (!fallbackAdShowing) webView.onResume()
            }
        )
        switcher.attach(findViewById(android.R.id.content))

        if (savedInstanceState == null) {
            webView.loadUrl(GameCatalog.games[currentGame].url)
        } else {
            currentGame = savedInstanceState.getInt(KEY_GAME, GameCatalog.latestIndex)
            webView.restoreState(savedInstanceState)
        }
        switcher.setCurrent(currentGame)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putInt(KEY_GAME, currentGame)
        webView.saveState(outState)
    }

    private fun switchTo(index: Int) {
        if (index == currentGame) return
        currentGame = index
        val url = GameCatalog.games[index].url
        webView.animate().alpha(0f).setDuration(140).withEndAction { webView.loadUrl(url) }.start()
    }

    override fun onResume() {
        super.onResume()
        if (!fallbackAdShowing && !sheetOpen) webView.onResume()
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
        switcher.destroy()
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

    private fun postInterstitialResult(result: String) {
        runOnUiThread {
            pendingInterstitialResult = result
            if (lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) flushAdResult()
        }
    }

    private fun flushAdResult() {
        val result = pendingAdResult
        if (result != null) {
            pendingAdResult = null
            webView.evaluateJavascript(
                "window.onRewardedAdResult && window.onRewardedAdResult('$result')",
                null
            )
        }
        val interstitialResult = pendingInterstitialResult
        if (interstitialResult != null) {
            pendingInterstitialResult = null
            webView.evaluateJavascript(
                "window.onInterstitialAdResult && window.onInterstitialAdResult('$interstitialResult')",
                null
            )
        }
    }

    private inner class AdBridge {
        @JavascriptInterface
        fun showRewardedAd() {
            runOnUiThread { rewardedAds.loadAndShow() }
        }

        @JavascriptInterface
        fun showInterstitialAd() {
            runOnUiThread { interstitialAds.loadAndShow() }
        }

        @JavascriptInterface
        fun getCooldownMs(): Long = rewardedAds.remainingCooldownMs()
    }

    companion object {
        private const val ASSET_PREFIX = "file:///android_asset/"
        private const val KEY_GAME = "current_game"
        private const val RESULT_EARNED = "earned"
        private const val RESULT_SHOWN = "shown"
        private const val RESULT_FAILED = "failed"
    }
}
