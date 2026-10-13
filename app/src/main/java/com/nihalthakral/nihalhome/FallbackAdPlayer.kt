package com.nihalthakral.nihalhome

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.VideoView
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import kotlin.math.ceil
import kotlin.math.min

class FallbackAdPlayer(
    private val activity: ComponentActivity,
    private val showReward: Boolean = true,
    private val onClosed: (Boolean) -> Unit
) {

    private val handler = Handler(Looper.getMainLooper())
    private val density = activity.resources.displayMetrics.density
    private val videoUri = Uri.parse("android.resource://${activity.packageName}/${R.raw.ad}")

    private lateinit var layer: FrameLayout
    private lateinit var video: VideoView
    private lateinit var infoGroup: LinearLayout
    private lateinit var rewardText: TextView
    private lateinit var closeButton: CloseButton

    private var completed = false
    private var finished = false
    private var stopped = false
    private var resumePosition = 0

    private val ticker = object : Runnable {
        override fun run() {
            if (finished || completed) return
            updateReward()
            handler.postDelayed(this, 200L)
        }
    }

    private val backCallback = object : OnBackPressedCallback(true) {
        override fun handleOnBackPressed() {
            if (completed) close()
        }
    }

    private val lifecycleObserver = LifecycleEventObserver { _, event ->
        if (finished) return@LifecycleEventObserver
        if (event == Lifecycle.Event.ON_STOP && !completed) {
            resumePosition = video.currentPosition
            video.stopPlayback()
            handler.removeCallbacks(ticker)
            stopped = true
        }
        if (event == Lifecycle.Event.ON_START && stopped) {
            stopped = false
            video.setVideoURI(videoUri)
        }
        if (event == Lifecycle.Event.ON_DESTROY) {
            teardown()
        }
    }

    fun show() {
        val content = activity.findViewById<ViewGroup>(android.R.id.content)

        layer = FrameLayout(activity).apply {
            setBackgroundColor(Color.BLACK)
            isClickable = true
            isFocusable = true
            keepScreenOn = true
        }

        video = VideoView(activity)
        layer.addView(
            video,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
                Gravity.CENTER
            )
        )

        val bar = FrameLayout(activity)
        layer.addView(
            bar,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.TOP
            )
        )

        infoGroup = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            visibility = View.INVISIBLE
        }

        rewardText = TextView(activity).apply {
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            typeface = Typeface.DEFAULT_BOLD
            setPadding(dp(12), dp(7), dp(12), dp(7))
            background = rounded(Color.argb(153, 0, 0, 0), 16)
        }

        val adBadge = TextView(activity).apply {
            text = "Ad"
            setTextColor(Color.BLACK)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
            typeface = Typeface.DEFAULT_BOLD
            setPadding(dp(6), dp(2), dp(6), dp(2))
            background = rounded(Color.rgb(251, 188, 4), 4)
        }

        if (showReward) {
            infoGroup.addView(
                rewardText,
                LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
            )
        }
        infoGroup.addView(
            adBadge,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { marginStart = if (showReward) dp(8) else 0 }
        )
        bar.addView(
            infoGroup,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.START or Gravity.CENTER_VERTICAL
            )
        )

        closeButton = CloseButton(activity).apply {
            visibility = View.GONE
            contentDescription = "Close ad"
            setOnClickListener { close() }
        }
        bar.addView(
            closeButton,
            FrameLayout.LayoutParams(dp(40), dp(40), Gravity.END or Gravity.CENTER_VERTICAL)
        )

        ViewCompat.setOnApplyWindowInsetsListener(layer) { _, insets ->
            val bars = insets.getInsets(
                WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout()
            )
            bar.setPadding(
                dp(12) + bars.left,
                dp(12) + bars.top,
                dp(12) + bars.right,
                dp(12)
            )
            insets
        }

        content.addView(
            layer,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        )
        ViewCompat.requestApplyInsets(layer)

        activity.onBackPressedDispatcher.addCallback(activity, backCallback)
        activity.lifecycle.addObserver(lifecycleObserver)

        video.setOnPreparedListener { player ->
            player.isLooping = false
            if (resumePosition > 0) video.seekTo(resumePosition)
            video.start()
            handler.removeCallbacks(ticker)
            handler.post(ticker)
        }
        video.setOnCompletionListener { onVideoCompleted() }
        video.setOnErrorListener { _, _, _ ->
            close()
            true
        }
        video.setVideoURI(videoUri)
    }

    private fun updateReward() {
        val total = video.duration
        if (total <= 0) return
        if (!showReward) {
            infoGroup.visibility = View.VISIBLE
            return
        }
        val left = ceil((total - video.currentPosition) / 1000.0).toInt().coerceAtLeast(0)
        rewardText.text = "Reward in $left"
        infoGroup.visibility = View.VISIBLE
    }

    private fun onVideoCompleted() {
        if (finished || completed) return
        completed = true
        handler.removeCallbacks(ticker)
        if (showReward) rewardText.text = "Reward granted"
        infoGroup.visibility = View.VISIBLE
        closeButton.alpha = 0f
        closeButton.visibility = View.VISIBLE
        closeButton.animate().alpha(1f).setDuration(250L).start()
    }

    private fun teardown() {
        finished = true
        handler.removeCallbacksAndMessages(null)
        backCallback.remove()
        activity.lifecycle.removeObserver(lifecycleObserver)
    }

    private fun close() {
        if (finished) return
        teardown()
        video.stopPlayback()
        (layer.parent as? ViewGroup)?.removeView(layer)
        onClosed(completed)
    }

    private fun dp(value: Int): Int = (value * density).toInt()

    private fun rounded(color: Int, radiusDp: Int): GradientDrawable =
        GradientDrawable().apply {
            setColor(color)
            cornerRadius = dp(radiusDp).toFloat()
        }

    private class CloseButton(context: Context) : View(context) {
        private val strokeWidthPx = 2f * context.resources.displayMetrics.density

        private val circlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(153, 0, 0, 0)
            style = Paint.Style.FILL
        }

        private val crossPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            style = Paint.Style.STROKE
            strokeWidth = strokeWidthPx
            strokeCap = Paint.Cap.ROUND
        }

        override fun onDraw(canvas: Canvas) {
            val cx = width / 2f
            val cy = height / 2f
            canvas.drawCircle(cx, cy, min(cx, cy) * 0.8f, circlePaint)
            val a = min(cx, cy) * 0.3f
            canvas.drawLine(cx - a, cy - a, cx + a, cy + a, crossPaint)
            canvas.drawLine(cx - a, cy + a, cx + a, cy - a, crossPaint)
        }
    }
}
