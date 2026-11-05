package org.jarsi.devicewatch.overlay

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewGroup
import android.view.WindowInsets
import android.view.WindowManager
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import androidx.annotation.MainThread
import org.jarsi.devicewatch.R
import org.jarsi.devicewatch.quick.QuickMetricReading

/**
 * The floating card itself: a small dark window over other apps with one to three
 * figures. Plain Views, because a window owned by a service has no Compose owners.
 * It is dragged by touch, a tap shows a hide and an open button for a few seconds,
 * and a long press hides it. Every call must come on the main thread; every
 * decision comes from [FloatingFigureLogic].
 */
@MainThread
class FloatingFigureWindow(
    private val context: Context,
    private val onHide: () -> Unit,
    private val onOpenApp: () -> Unit,
    private val onPositionChanged: (landscape: Boolean, OverlayPosition) -> Unit,
    /** The window could not be added or moved: the permission is gone, or the token is bad. */
    private val onFailed: () -> Unit,
) {
    private val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private val handler = Handler(Looper.getMainLooper())
    private val gesture = OverlayGesture(ViewConfiguration.get(context).scaledTouchSlop.toFloat(), OVERLAY_LONG_PRESS_MS)

    private var root: LinearLayout? = null
    private var figures: LinearLayout? = null
    private var buttons: LinearLayout? = null
    private var params: WindowManager.LayoutParams? = null
    private var tileLabels: Map<QuickMetricReading, Pair<TextView, TextView>> = emptyMap()
    private var shownMetrics: List<String> = emptyList()
    private var landscape = false
    private var position: OverlayPosition = OVERLAY_DEFAULT_POSITION

    /** Where the drag began, in window coordinates, and the finger's screen position then. */
    private var dragStartX = 0
    private var dragStartY = 0
    private var longPressFired = false

    private val hideButtons = Runnable { buttons?.visibility = View.GONE; relayout() }
    private val longPress = Runnable {
        longPressFired = true
        onHide()
    }

    val isShowing: Boolean get() = root != null

    /** Puts the card up with [readings] at [position] (null: the default corner), rebuilding it if already up. */
    fun show(readings: List<QuickMetricReading>, position: OverlayPosition?, landscape: Boolean, black: Boolean) {
        this.landscape = landscape
        this.position = position ?: OVERLAY_DEFAULT_POSITION
        if (root != null) hide()
        val card = buildCard(readings, black)
        val lp = WindowManager.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            // Not focusable and not touch-modal: keys and touches outside the card go to the app beneath.
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            title = context.getString(R.string.overlay_card_description)
        }
        val (x, y) = pixelsFor(card, lp)
        lp.x = x
        lp.y = y
        try {
            windowManager.addView(card, lp)
        } catch (e: SecurityException) {
            onFailed()
            return
        } catch (e: WindowManager.BadTokenException) {
            onFailed()
            return
        } catch (e: IllegalStateException) {
            // Already added under a token this window no longer holds.
            onFailed()
            return
        }
        root = card
        params = lp
    }

    /** New figures for a card already up; one with other figures is rebuilt in place. */
    fun update(readings: List<QuickMetricReading>) {
        val card = root ?: return
        if (readings.map { it.metric.name } != shownMetrics) {
            val black = (card.background as? GradientDrawable)?.color?.defaultColor == Color.BLACK
            show(readings, position, landscape, black)
            return
        }
        bind(readings)
        relayout()
    }

    fun hide() {
        val card = root ?: return
        handler.removeCallbacks(hideButtons)
        handler.removeCallbacks(longPress)
        try {
            windowManager.removeViewImmediate(card)
        } catch (e: IllegalArgumentException) {
            // Never attached, or already taken away with the permission.
        }
        root = null
        figures = null
        buttons = null
        params = null
        tileLabels = emptyMap()
        shownMetrics = emptyList()
    }

    // ---- building ----

    private fun buildCard(readings: List<QuickMetricReading>, black: Boolean): LinearLayout {
        val card = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            background = GradientDrawable().apply {
                cornerRadius = dp(12f)
                setColor(if (black) Color.BLACK else CARD_COLOUR)
                alpha = CARD_ALPHA
            }
            setPadding(dp(10f).toInt(), dp(8f).toInt(), dp(10f).toInt(), dp(8f).toInt())
            contentDescription = context.getString(R.string.overlay_card_description)
            isClickable = true
            isLongClickable = true
        }
        val figureRow = LinearLayout(context).apply {
            orientation = when (overlayLayout(readings.size, landscape)) {
                OverlayLayout.ROW -> LinearLayout.HORIZONTAL
                OverlayLayout.COLUMN -> LinearLayout.VERTICAL
            }
            gravity = Gravity.CENTER
        }
        val labels = mutableMapOf<QuickMetricReading, Pair<TextView, TextView>>()
        readings.forEachIndexed { index, reading ->
            val value = TextView(context).apply {
                typeface = Typeface.MONOSPACE
                setTypeface(typeface, Typeface.BOLD)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
                setTextColor(Color.WHITE)
                maxLines = 1
                gravity = Gravity.CENTER_HORIZONTAL
            }
            val label = TextView(context).apply {
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
                setTextColor(LABEL_COLOUR)
                maxLines = 1
                gravity = Gravity.CENTER_HORIZONTAL
            }
            val figure = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER_HORIZONTAL
                addView(value)
                addView(label)
            }
            val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            if (index > 0) {
                if (figureRow.orientation == LinearLayout.HORIZONTAL) lp.marginStart = dp(14f).toInt()
                else lp.topMargin = dp(6f).toInt()
            }
            figureRow.addView(figure, lp)
            labels[reading] = value to label
        }
        val buttonRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            visibility = View.GONE
            addView(button(R.drawable.ic_overlay_close, R.string.overlay_close) { onHide() })
            addView(button(R.drawable.ic_open_in_new, R.string.overlay_open_app) { onOpenApp() })
        }
        card.addView(figureRow)
        card.addView(buttonRow, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            topMargin = dp(2f).toInt()
        })
        card.setOnTouchListener { _, event -> onTouch(event) }
        // Accessibility services click and long-click without touching.
        card.setOnClickListener { revealButtons() }
        card.setOnLongClickListener { onHide(); true }
        figures = figureRow
        buttons = buttonRow
        tileLabels = labels
        shownMetrics = readings.map { it.metric.name }
        bind(readings)
        return card
    }

    private fun button(iconRes: Int, descriptionRes: Int, onClick: () -> Unit): ImageButton =
        ImageButton(context).apply {
            setImageResource(iconRes)
            imageTintList = android.content.res.ColorStateList.valueOf(Color.WHITE)
            background = null
            contentDescription = context.getString(descriptionRes)
            layoutParams = LinearLayout.LayoutParams(dp(48f).toInt(), dp(48f).toInt())
            setOnClickListener { onClick() }
        }

    private fun bind(readings: List<QuickMetricReading>) {
        val byMetric = tileLabels.entries.associate { it.key.metric to it.value }
        readings.forEach { reading ->
            val (value, label) = byMetric[reading.metric] ?: return@forEach
            value.text = reading.value
            label.text = labelText(reading)
        }
    }

    /** The figure's short name, with a short detail (the battery's status, charging or discharging) after it. */
    private fun labelText(reading: QuickMetricReading): String {
        val name = context.getString(org.jarsi.devicewatch.quick.quickMetricTileLabelRes(reading.metric))
        val detail = reading.detail?.takeIf { it.length <= SHORT_DETAIL_CHARS }
        return if (detail == null) name else "$name · $detail"
    }

    // ---- placing ----

    /** The room the card has: the screen without its system bars. */
    private fun room(): Room {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val metrics = windowManager.currentWindowMetrics
            val insets = metrics.windowInsets.getInsetsIgnoringVisibility(WindowInsets.Type.systemBars())
            return Room(
                width = metrics.bounds.width() - insets.left - insets.right,
                height = metrics.bounds.height() - insets.top - insets.bottom,
            )
        }
        // Before Android 11 the size without the navigation bar is all that is public; the
        // status bar's height is counted as room, so at the very bottom the card may sit a
        // little low on those phones.
        @Suppress("DEPRECATION")
        val display = windowManager.defaultDisplay
        val size = android.graphics.Point()
        @Suppress("DEPRECATION")
        display.getSize(size)
        return Room(width = size.x, height = size.y)
    }

    private fun measured(card: View): Pair<Int, Int> {
        card.measure(View.MeasureSpec.UNSPECIFIED, View.MeasureSpec.UNSPECIFIED)
        return card.measuredWidth to card.measuredHeight
    }

    private fun pixelsFor(card: View, lp: WindowManager.LayoutParams): Pair<Int, Int> {
        val (w, h) = measured(card)
        val room = room()
        return overlayPixels(position, w, h, room.width, room.height)
    }

    /** Re-measures the card and keeps it on screen at its share of the room. */
    private fun relayout() {
        val card = root ?: return
        val lp = params ?: return
        val (x, y) = pixelsFor(card, lp)
        lp.x = x
        lp.y = y
        applyLayout(card, lp)
    }

    private fun applyLayout(card: View, lp: WindowManager.LayoutParams) {
        try {
            windowManager.updateViewLayout(card, lp)
        } catch (e: IllegalArgumentException) {
            onFailed()
        } catch (e: SecurityException) {
            onFailed()
        }
    }

    // ---- touching ----

    // A tap goes through performClick, so accessibility services see the click; the
    // long press and the drag are the touch listener's own.
    @SuppressLint("ClickableViewAccessibility")
    private fun onTouch(event: MotionEvent): Boolean {
        val card = root ?: return false
        val lp = params ?: return false
        val now = SystemClock.uptimeMillis()
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                gesture.down(event.rawX, event.rawY, now)
                dragStartX = lp.x
                dragStartY = lp.y
                longPressFired = false
                handler.removeCallbacks(longPress)
                handler.postDelayed(longPress, OVERLAY_LONG_PRESS_MS)
            }
            MotionEvent.ACTION_MOVE -> {
                val delta = gesture.move(event.rawX, event.rawY, now) ?: return true
                handler.removeCallbacks(longPress)
                val room = room()
                val (w, h) = card.width to card.height
                lp.x = (dragStartX + delta.first.toInt()).coerceIn(0, maxOf(0, room.width - w))
                lp.y = (dragStartY + delta.second.toInt()).coerceIn(0, maxOf(0, room.height - h))
                applyLayout(card, lp)
            }
            MotionEvent.ACTION_UP -> {
                handler.removeCallbacks(longPress)
                if (longPressFired) return true
                when (gesture.up(now)) {
                    OverlayTap.TAP -> card.performClick()
                    OverlayTap.LONG_PRESS -> onHide()
                    OverlayTap.NONE -> {
                        val room = room()
                        position = overlayFraction(lp.x, lp.y, card.width, card.height, room.width, room.height)
                        onPositionChanged(landscape, position)
                    }
                }
            }
            MotionEvent.ACTION_CANCEL -> {
                handler.removeCallbacks(longPress)
                if (gesture.up(now) == OverlayTap.NONE) {
                    val room = room()
                    position = overlayFraction(lp.x, lp.y, card.width, card.height, room.width, room.height)
                    onPositionChanged(landscape, position)
                }
            }
        }
        return true
    }

    private fun revealButtons() {
        val row = buttons ?: return
        row.visibility = View.VISIBLE
        relayout()
        handler.removeCallbacks(hideButtons)
        handler.postDelayed(hideButtons, OVERLAY_BUTTONS_VISIBLE_MS)
    }

    private fun dp(value: Float): Float =
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, value, context.resources.displayMetrics)

    private data class Room(val width: Int, val height: Int)

    private companion object {
        /** The dark widget colour, so the card matches the widgets over a bright game. */
        const val CARD_COLOUR = 0xFF12151A.toInt()
        /** About nine tenths opaque; the text stays fully opaque. */
        const val CARD_ALPHA = 230
        val LABEL_COLOUR = Color.argb(0xB3, 0xFF, 0xFF, 0xFF)
        /** A detail this short rides beside the name: "lataa", "Ladataan"; a data breakdown does not. */
        const val SHORT_DETAIL_CHARS = 12
    }
}
