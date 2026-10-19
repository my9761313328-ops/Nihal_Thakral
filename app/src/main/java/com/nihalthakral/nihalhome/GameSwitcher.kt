package com.nihalthakral.nihalhome

import android.app.Activity
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.util.TypedValue
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.animation.DecelerateInterpolator
import android.view.animation.OvershootInterpolator
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import kotlin.random.Random

class GameSwitcher(
    private val activity: Activity,
    private val games: List<GameInfo>,
    private val onPick: (Int) -> Unit,
    private val onToggle: (Boolean) -> Unit
) {

    private val density = activity.resources.displayMetrics.density
    private val screenH = activity.resources.displayMetrics.heightPixels
    private val handler = Handler(Looper.getMainLooper())

    private val overlay = FrameLayout(activity)
    private val scrim = View(activity)
    private val sheet = LinearLayout(activity)
    private val list = LinearLayout(activity)
    private val handle = TextView(activity)
    private val dimTask = Runnable { handle.animate().alpha(0.35f).setStartDelay(0).setDuration(400).setInterpolator(DecelerateInterpolator()).start() }

    var currentIndex: Int = 0
        private set

    var isOpen: Boolean = false
        private set

    init {
        buildOverlay()
        buildHandle()
    }

    fun attach(parent: ViewGroup) {
        parent.addView(
            overlay,
            FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        )
        parent.addView(
            handle,
            FrameLayout.LayoutParams(dp(40), dp(56), Gravity.START or Gravity.CENTER_VERTICAL)
        )
        handle.translationX = -dp(44).toFloat()
        handle.animate()
            .translationX(0f)
            .setStartDelay(700)
            .setDuration(450)
            .setInterpolator(OvershootInterpolator(1.4f))
            .start()
        handler.postDelayed(dimTask, 4200)
    }

    fun setCurrent(index: Int) {
        currentIndex = index
    }

    fun open() {
        if (isOpen) return
        isOpen = true
        handler.removeCallbacks(dimTask)
        handle.animate().cancel()
        handle.visibility = View.GONE
        populate()
        overlay.visibility = View.VISIBLE
        scrim.alpha = 0f
        scrim.animate().alpha(1f).setDuration(220).start()
        sheet.animate().cancel()
        sheet.translationY = screenH.toFloat()
        sheet.animate()
            .translationY(0f)
            .setDuration(320)
            .setInterpolator(DecelerateInterpolator(2f))
            .start()
        onToggle(true)
    }

    fun close() {
        if (!isOpen) return
        isOpen = false
        scrim.animate().alpha(0f).setDuration(200).start()
        sheet.animate().cancel()
        sheet.animate()
            .translationY(screenH.toFloat())
            .setDuration(240)
            .withEndAction { if (!isOpen) overlay.visibility = View.GONE }
            .start()
        handle.visibility = View.VISIBLE
        handle.alpha = 1f
        handler.removeCallbacks(dimTask)
        handler.postDelayed(dimTask, 2500)
        onToggle(false)
    }

    private fun pick(index: Int) {
        if (index == currentIndex) {
            close()
            return
        }
        currentIndex = index
        close()
        onPick(index)
    }

    private fun buildOverlay() {
        overlay.visibility = View.GONE
        overlay.isClickable = true

        scrim.setBackgroundColor(Color.parseColor("#B3050716"))
        scrim.setOnClickListener { close() }
        overlay.addView(
            scrim,
            FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        )

        sheet.orientation = LinearLayout.VERTICAL
        sheet.setPadding(dp(18), dp(10), dp(18), dp(26))
        sheet.isClickable = true
        sheet.background = GradientDrawable().apply {
            setColor(Color.parseColor("#141734"))
            val r = dp(28).toFloat()
            cornerRadii = floatArrayOf(r, r, r, r, 0f, 0f, 0f, 0f)
            setStroke(dp(1), Color.parseColor("#337DD3FC"))
        }

        val grip = View(activity).apply {
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#55FFFFFF"))
                cornerRadius = dp(3).toFloat()
            }
        }
        sheet.addView(grip, LinearLayout.LayoutParams(dp(44), dp(5)).apply {
            gravity = Gravity.CENTER_HORIZONTAL
            bottomMargin = dp(14)
        })

        sheet.addView(label("Choose your game", 22f, Color.WHITE, true))
        sheet.addView(label("${games.size} games  ·  pick one and play", 13f, Color.parseColor("#99FFFFFF"), false).apply {
            setPadding(0, dp(2), 0, dp(14))
        })

        list.orientation = LinearLayout.VERTICAL
        val scroll = CappedScroll(activity, (screenH * 0.55f).toInt()).apply {
            isVerticalScrollBarEnabled = false
            overScrollMode = View.OVER_SCROLL_NEVER
            addView(list, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        }
        sheet.addView(scroll, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))

        overlay.addView(
            sheet,
            FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM)
        )
    }

    private fun buildHandle() {
        handle.text = "🎮"
        handle.setTextSize(TypedValue.COMPLEX_UNIT_SP, 20f)
        handle.gravity = Gravity.CENTER
        handle.contentDescription = "Switch game"
        val r = dp(28).toFloat()
        handle.background = GradientDrawable().apply {
            setColor(Color.parseColor("#B30F1230"))
            cornerRadii = floatArrayOf(0f, 0f, r, r, r, r, 0f, 0f)
            setStroke(dp(1), Color.parseColor("#667DD3FC"))
        }
        handle.setOnClickListener {
            it.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
            open()
        }
    }

    private fun populate() {
        list.removeAllViews()
        var order = 0
        for (i in games.indices.reversed()) {
            val card = buildCard(i)
            list.addView(card, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                bottomMargin = dp(12)
            })
            card.alpha = 0f
            card.translationY = dp(40).toFloat()
            card.animate()
                .alpha(1f)
                .translationY(0f)
                .setStartDelay(120L + order * 70L)
                .setDuration(300)
                .setInterpolator(DecelerateInterpolator(2f))
                .start()
            order++
        }
        if (games.size > 2) {
            val surprise = label("🎲  Surprise me", 15f, Color.WHITE, true).apply {
                gravity = Gravity.CENTER
                setPadding(0, dp(14), 0, dp(14))
                background = GradientDrawable().apply {
                    setColor(Color.parseColor("#14FFFFFF"))
                    cornerRadius = dp(18).toFloat()
                    setStroke(dp(1), Color.parseColor("#447DD3FC"))
                }
                setOnClickListener {
                    performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                    val others = games.indices.filter { it != currentIndex }
                    pick(others[Random.nextInt(others.size)])
                }
            }
            list.addView(surprise, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
            pressEffect(surprise)
        }
    }

    private fun buildCard(index: Int): View {
        val game = games[index]
        val isCurrent = index == currentIndex
        val isLatest = index == games.lastIndex

        val card = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(14), dp(14), dp(14), dp(14))
            background = GradientDrawable(
                GradientDrawable.Orientation.LEFT_RIGHT,
                intArrayOf(game.colorStart, game.colorEnd)
            ).apply {
                cornerRadius = dp(22).toFloat()
                if (isCurrent) setStroke(dp(2), Color.WHITE)
            }
            setOnClickListener {
                it.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                pick(index)
            }
        }
        pressEffect(card)

        val tile = TextView(activity).apply {
            text = game.emoji
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 30f)
            gravity = Gravity.CENTER
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#33FFFFFF"))
                cornerRadius = dp(16).toFloat()
            }
        }
        card.addView(tile, LinearLayout.LayoutParams(dp(58), dp(58)))

        val col = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), 0, dp(8), 0)
        }
        col.addView(label(game.title, 18f, Color.WHITE, true))
        col.addView(label(game.tagline, 13f, Color.parseColor("#CCFFFFFF"), false).apply {
            setPadding(0, dp(2), 0, 0)
        })
        card.addView(col, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))

        val chipText = when {
            isCurrent -> "PLAYING"
            isLatest -> "NEW"
            else -> ""
        }
        if (chipText.isNotEmpty()) {
            val chip = label(chipText, 10f, Color.parseColor("#141734"), true).apply {
                letterSpacing = 0.12f
                setPadding(dp(10), dp(5), dp(10), dp(5))
                background = GradientDrawable().apply {
                    setColor(if (isCurrent) Color.WHITE else Color.parseColor("#FBBF24"))
                    cornerRadius = dp(12).toFloat()
                }
            }
            card.addView(chip, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        }
        return card
    }

    private fun pressEffect(v: View) {
        v.setOnTouchListener { view, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> view.animate().scaleX(0.96f).scaleY(0.96f).setDuration(90).start()
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL ->
                    view.animate().scaleX(1f).scaleY(1f).setDuration(140).start()
            }
            false
        }
    }

    private fun label(text: String, sp: Float, color: Int, bold: Boolean): TextView =
        TextView(activity).apply {
            this.text = text
            setTextSize(TypedValue.COMPLEX_UNIT_SP, sp)
            setTextColor(color)
            if (bold) typeface = Typeface.DEFAULT_BOLD
        }

    private fun dp(v: Int): Int = (v * density).toInt()

    private class CappedScroll(activity: Activity, private val cap: Int) : ScrollView(activity) {
        override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
            super.onMeasure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(cap, MeasureSpec.AT_MOST))
        }
    }
}
