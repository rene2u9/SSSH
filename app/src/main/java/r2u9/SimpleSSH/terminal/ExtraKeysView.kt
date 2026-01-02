package r2u9.SimpleSSH.terminal

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.os.Handler
import android.os.Looper
import android.util.AttributeSet
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import kotlin.math.min

/**
 * Extra keys view that displays two rows of special keys above the keyboard.
 * Inspired by Termux's ExtraKeysView implementation.
 */
class ExtraKeysView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    interface OnKeyListener {
        fun onKey(key: String, code: Int)
        fun onModifierChanged(ctrl: Boolean, alt: Boolean, shift: Boolean, fn: Boolean)
    }

    var keyListener: OnKeyListener? = null

    var accentColor: Int = 0xFF448AFF.toInt()
        set(value) {
            field = value
            textActivePaint.color = value
            invalidate()
        }

    var buttonBackgroundColor: Int = 0xFF000000.toInt()
        set(value) {
            field = value
            bgPaint.color = value
            keyBgPaint.color = value
            invalidate()
        }

    var buttonTextColor: Int = 0xFFFFFFFF.toInt()
        set(value) {
            field = value
            textPaint.color = value
            invalidate()
        }

    // Modifier states
    private var ctrlActive = false
    private var altActive = false
    private var shiftActive = false
    private var fnActive = false

    // Key definitions
    data class ExtraKey(
        val display: String,
        val key: String,
        val code: Int = 0,
        val isModifier: Boolean = false,
        val repeatable: Boolean = false
    )

    // Two rows of keys (Termux style)
    private val row1 = listOf(
        ExtraKey("ESC", "\u001b", 111),
        ExtraKey("/", "/"),
        ExtraKey("-", "-"),
        ExtraKey("HOME", "\u001b[H"),
        ExtraKey("↑", "\u001b[A", 19, repeatable = true),
        ExtraKey("END", "\u001b[F"),
        ExtraKey("PGUP", "\u001b[5~")
    )

    private val row2 = listOf(
        ExtraKey("TAB", "\t", 61),
        ExtraKey("CTRL", "CTRL", isModifier = true),
        ExtraKey("ALT", "ALT", isModifier = true),
        ExtraKey("←", "\u001b[D", 21, repeatable = true),
        ExtraKey("↓", "\u001b[B", 20, repeatable = true),
        ExtraKey("→", "\u001b[C", 22, repeatable = true),
        ExtraKey("PGDN", "\u001b[6~")
    )

    // Paint objects
    private val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF000000.toInt()
    }
    private val keyBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF000000.toInt()
    }
    private val keyBgPressedPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF222222.toInt()
    }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFFFFFFFF.toInt()
        textAlign = Paint.Align.CENTER
        typeface = Typeface.DEFAULT_BOLD
    }
    private val textActivePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF448AFF.toInt() // Accent color
        textAlign = Paint.Align.CENTER
        typeface = Typeface.DEFAULT_BOLD
    }

    private val row1Rects = mutableListOf<RectF>()
    private val row2Rects = mutableListOf<RectF>()
    private var pressedRow = -1
    private var pressedIndex = -1
    private val handler = Handler(Looper.getMainLooper())
    private var repeatRunnable: Runnable? = null

    init {
        isClickable = true
        isFocusable = false
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val width = MeasureSpec.getSize(widthMeasureSpec)
        val height = (80 * resources.displayMetrics.density).toInt() // 2 rows
        setMeasuredDimension(width, height)
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        calculateKeyRects()
    }

    private fun calculateKeyRects() {
        row1Rects.clear()
        row2Rects.clear()
        val padding = 3 * resources.displayMetrics.density
        val rowHeight = (height - padding * 3) / 2
        val numKeys = maxOf(row1.size, row2.size)
        val keyWidth = (width - padding * (numKeys + 1)) / numKeys

        val textSize = min(rowHeight * 0.45f, 14 * resources.displayMetrics.density)
        textPaint.textSize = textSize
        textActivePaint.textSize = textSize

        // Row 1
        var x = padding
        for (key in row1) {
            row1Rects.add(RectF(x, padding, x + keyWidth, padding + rowHeight))
            x += keyWidth + padding
        }

        // Row 2
        x = padding
        val row2Top = padding * 2 + rowHeight
        for (key in row2) {
            row2Rects.add(RectF(x, row2Top, x + keyWidth, row2Top + rowHeight))
            x += keyWidth + padding
        }
    }

    override fun onDraw(canvas: Canvas) {
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), bgPaint)

        // Draw row 1
        for ((index, key) in row1.withIndex()) {
            val rect = row1Rects.getOrNull(index) ?: continue
            drawKey(canvas, key, rect, pressedRow == 0 && pressedIndex == index)
        }

        // Draw row 2
        for ((index, key) in row2.withIndex()) {
            val rect = row2Rects.getOrNull(index) ?: continue
            drawKey(canvas, key, rect, pressedRow == 1 && pressedIndex == index)
        }
    }

    private fun drawKey(canvas: Canvas, key: ExtraKey, rect: RectF, isPressed: Boolean) {
        val isModifierActive = when (key.key) {
            "CTRL" -> ctrlActive
            "ALT" -> altActive
            "SHIFT" -> shiftActive
            "FN" -> fnActive
            else -> false
        }

        val bgPaint = if (isPressed) keyBgPressedPaint else keyBgPaint
        val txtPaint = if (isModifierActive) textActivePaint else textPaint

        canvas.drawRoundRect(rect, 6f, 6f, bgPaint)
        canvas.drawText(
            key.display,
            rect.centerX(),
            rect.centerY() - (txtPaint.descent() + txtPaint.ascent()) / 2,
            txtPaint
        )
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                val (row, index) = findKeyAt(event.x, event.y)
                if (row >= 0 && index >= 0) {
                    pressedRow = row
                    pressedIndex = index
                    performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)

                    val key = getKey(row, index)
                    if (key?.repeatable == true) {
                        startRepeat(key)
                    }

                    invalidate()
                }
            }
            MotionEvent.ACTION_MOVE -> {
                val (row, index) = findKeyAt(event.x, event.y)
                if (row != pressedRow || index != pressedIndex) {
                    stopRepeat()
                    pressedRow = row
                    pressedIndex = index
                    val key = getKey(row, index)
                    if (key?.repeatable == true) {
                        startRepeat(key)
                    }
                    invalidate()
                }
            }
            MotionEvent.ACTION_UP -> {
                stopRepeat()
                val key = getKey(pressedRow, pressedIndex)
                if (key != null) {
                    handleKeyPress(key)
                }
                pressedRow = -1
                pressedIndex = -1
                invalidate()
            }
            MotionEvent.ACTION_CANCEL -> {
                stopRepeat()
                pressedRow = -1
                pressedIndex = -1
                invalidate()
            }
        }
        return true
    }

    private fun findKeyAt(x: Float, y: Float): Pair<Int, Int> {
        for ((index, rect) in row1Rects.withIndex()) {
            if (rect.contains(x, y)) return 0 to index
        }
        for ((index, rect) in row2Rects.withIndex()) {
            if (rect.contains(x, y)) return 1 to index
        }
        return -1 to -1
    }

    private fun getKey(row: Int, index: Int): ExtraKey? {
        return when (row) {
            0 -> row1.getOrNull(index)
            1 -> row2.getOrNull(index)
            else -> null
        }
    }

    private fun handleKeyPress(key: ExtraKey) {
        when (key.key) {
            "CTRL" -> {
                ctrlActive = !ctrlActive
                notifyModifierChanged()
            }
            "ALT" -> {
                altActive = !altActive
                notifyModifierChanged()
            }
            "SHIFT" -> {
                shiftActive = !shiftActive
                notifyModifierChanged()
            }
            "FN" -> {
                fnActive = !fnActive
                notifyModifierChanged()
            }
            else -> {
                keyListener?.onKey(key.key, key.code)
                // Reset modifiers after key press (one-shot mode)
                if (ctrlActive || altActive || shiftActive) {
                    ctrlActive = false
                    altActive = false
                    shiftActive = false
                    notifyModifierChanged()
                    invalidate()
                }
            }
        }
    }

    private fun notifyModifierChanged() {
        keyListener?.onModifierChanged(ctrlActive, altActive, shiftActive, fnActive)
        invalidate()
    }

    private fun startRepeat(key: ExtraKey) {
        repeatRunnable = object : Runnable {
            override fun run() {
                if (pressedIndex >= 0) {
                    keyListener?.onKey(key.key, key.code)
                    handler.postDelayed(this, 50)
                }
            }
        }
        handler.postDelayed(repeatRunnable!!, 400)
    }

    private fun stopRepeat() {
        repeatRunnable?.let { handler.removeCallbacks(it) }
        repeatRunnable = null
    }

    fun isCtrlActive() = ctrlActive
    fun isAltActive() = altActive
    fun isShiftActive() = shiftActive
    fun isFnActive() = fnActive

    fun resetModifiers() {
        ctrlActive = false
        altActive = false
        shiftActive = false
        fnActive = false
        notifyModifierChanged()
    }
}
