package r2u9.SimpleSSH.terminal

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.Typeface
import android.util.AttributeSet
import android.util.TypedValue
import android.view.GestureDetector
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import android.view.inputmethod.BaseInputConnection
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import android.view.inputmethod.InputMethodManager

class TerminalView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private var textSizeSp: Float = 14f
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.MONOSPACE
        textSize = spToPx(14f)
    }

    private var charWidth: Float = 0f
    private var charHeight: Float = 0f
    private var charBaseline: Float = 0f

    private var emulator: TerminalEmulator? = null
    private var onKeyInput: ((String) -> Unit)? = null
    private var onDoubleTap: (() -> Unit)? = null
    private var onLongPress: ((Float, Float) -> Unit)? = null
    private var onFontSizeChanged: ((Float) -> Unit)? = null

    // Pinch-to-zoom state
    private var isScaling = false
    private val minFontSize = 8f
    private val maxFontSize = 32f

    // Selection state
    private var isSelecting = false
    private var selectionStartX = 0
    private var selectionStartY = 0
    private var selectionEndX = 0
    private var selectionEndY = 0
    private val selectionPaint = Paint().apply {
        color = Color.argb(100, 100, 150, 255)
        style = Paint.Style.FILL
    }

    // Scroll state
    private var scrollAccumulator = 0f
    private val scrollThreshold: Float
        get() = charHeight

    private var cursorVisible = true
    private val cursorBlink: Runnable = object : Runnable {
        override fun run() {
            cursorVisible = !cursorVisible
            invalidate()
            postDelayed(this, 530)
        }
    }

    private val gestureDetector = GestureDetector(context, object : GestureDetector.SimpleOnGestureListener() {
        override fun onSingleTapUp(e: MotionEvent): Boolean {
            if (isSelecting) {
                clearSelection()
            } else {
                // Scroll to bottom on tap if scrolled up, otherwise show keyboard
                val emu = emulator
                if (emu != null && !emu.isAtBottom()) {
                    emu.scrollToBottom()
                } else {
                    showKeyboard()
                }
            }
            return true
        }

        override fun onDoubleTap(e: MotionEvent): Boolean {
            onDoubleTap?.invoke()
            return true
        }

        override fun onLongPress(e: MotionEvent) {
            onLongPress?.invoke(e.x, e.y)
        }

        override fun onScroll(
            e1: MotionEvent?,
            e2: MotionEvent,
            distanceX: Float,
            distanceY: Float
        ): Boolean {
            if (isSelecting) return false

            val emu = emulator ?: return false

            // Accumulate scroll distance (inverted for natural scrolling)
            scrollAccumulator -= distanceY

            // Convert accumulated distance to lines
            val linesToScroll = (scrollAccumulator / scrollThreshold).toInt()
            if (linesToScroll != 0) {
                if (linesToScroll > 0) {
                    // Finger moving up = showing older content (scroll up into history)
                    emu.scrollViewUp(linesToScroll)
                } else {
                    // Finger moving down = showing newer content (scroll down)
                    emu.scrollViewDown(-linesToScroll)
                }
                scrollAccumulator -= linesToScroll * scrollThreshold
            }
            return true
        }

        override fun onFling(
            e1: MotionEvent?,
            e2: MotionEvent,
            velocityX: Float,
            velocityY: Float
        ): Boolean {
            if (isSelecting) return false

            val emu = emulator ?: return false

            // Quick fling for fast scrolling (inverted for natural scrolling)
            val linesToScroll = (-velocityY / 500).toInt().coerceIn(-20, 20)
            if (linesToScroll > 0) {
                emu.scrollViewUp(linesToScroll)
            } else if (linesToScroll < 0) {
                emu.scrollViewDown(-linesToScroll)
            }
            return true
        }
    })

    private val scaleGestureDetector = ScaleGestureDetector(context, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
        override fun onScaleBegin(detector: ScaleGestureDetector): Boolean {
            isScaling = true
            return true
        }

        override fun onScale(detector: ScaleGestureDetector): Boolean {
            val scaleFactor = detector.scaleFactor
            val newSize = (textSizeSp * scaleFactor).coerceIn(minFontSize, maxFontSize)
            if (newSize != textSizeSp) {
                setTextSize(newSize)
                onFontSizeChanged?.invoke(newSize)
            }
            return true
        }

        override fun onScaleEnd(detector: ScaleGestureDetector) {
            isScaling = false
        }
    })

    private fun spToPx(sp: Float): Float {
        return TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_SP,
            sp,
            context.resources.displayMetrics
        )
    }

    init {
        isFocusable = true
        isFocusableInTouchMode = true
        updateCharMetrics()
    }

    private fun updateCharMetrics() {
        val rect = Rect()
        paint.getTextBounds("M", 0, 1, rect)
        charWidth = paint.measureText("M")
        charHeight = rect.height().toFloat() * 1.4f
        charBaseline = -rect.top.toFloat()
    }

    fun setTextSize(sizeSp: Float) {
        textSizeSp = sizeSp
        paint.textSize = spToPx(sizeSp)
        updateCharMetrics()
        requestLayout()
        invalidate()
    }

    fun getTextSize(): Float = textSizeSp

    fun setOnDoubleTapListener(listener: () -> Unit) {
        onDoubleTap = listener
    }

    fun setOnLongPressListener(listener: (Float, Float) -> Unit) {
        onLongPress = listener
    }

    fun setOnFontSizeChangedListener(listener: (Float) -> Unit) {
        onFontSizeChanged = listener
    }

    fun setEmulator(emulator: TerminalEmulator) {
        this.emulator = emulator
        emulator.onScreenUpdate = {
            post { invalidate() }
        }
        requestLayout()
        invalidate()
    }

    fun setOnKeyInput(listener: (String) -> Unit) {
        onKeyInput = listener
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        post(cursorBlink)
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        removeCallbacks(cursorBlink)
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val emu = emulator
        if (emu != null) {
            val desiredWidth = (emu.getColumns() * charWidth).toInt() + paddingLeft + paddingRight
            val desiredHeight = (emu.getRows() * charHeight).toInt() + paddingTop + paddingBottom

            val width = resolveSize(desiredWidth, widthMeasureSpec)
            val height = resolveSize(desiredHeight, heightMeasureSpec)
            setMeasuredDimension(width, height)
        } else {
            super.onMeasure(widthMeasureSpec, heightMeasureSpec)
        }
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        val cols = ((w - paddingLeft - paddingRight) / charWidth).toInt().coerceAtLeast(1)
        val rows = ((h - paddingTop - paddingBottom) / charHeight).toInt().coerceAtLeast(1)
        emulator?.resize(cols, rows)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val emu = emulator ?: return
        val theme = emu.getTheme()
        val screen = emu.getVisibleScreen()
        val isAtBottom = emu.isAtBottom()

        canvas.drawColor(theme.backgroundColor)

        for (y in 0 until emu.getRows()) {
            for (x in 0 until emu.getColumns()) {
                val cell = screen[y][x]
                val xPos = paddingLeft + x * charWidth
                val yPos = paddingTop + y * charHeight

                if (cell.background != theme.backgroundColor) {
                    paint.color = cell.background
                    canvas.drawRect(xPos, yPos, xPos + charWidth, yPos + charHeight, paint)
                }

                // Draw selection highlight
                if (isSelecting && isCellSelected(x, y)) {
                    canvas.drawRect(xPos, yPos, xPos + charWidth, yPos + charHeight, selectionPaint)
                }

                // Only show cursor when at bottom (current view)
                if (isAtBottom && x == emu.getCursorX() && y == emu.getCursorY() && cursorVisible && emu.isCursorVisible()) {
                    paint.color = theme.cursorColor
                    canvas.drawRect(xPos, yPos, xPos + charWidth, yPos + charHeight, paint)
                    paint.color = theme.backgroundColor
                } else {
                    paint.color = cell.foreground
                }

                if (cell.char != ' ' && cell.char.code >= 32) {
                    paint.isFakeBoldText = cell.bold
                    paint.textSkewX = if (cell.italic) -0.25f else 0f
                    canvas.drawText(cell.char.toString(), xPos, yPos + charBaseline, paint)

                    if (cell.underline) {
                        canvas.drawLine(xPos, yPos + charHeight - 2, xPos + charWidth, yPos + charHeight - 2, paint)
                    }
                }
            }
        }

    }

    private fun isCellSelected(x: Int, y: Int): Boolean {
        if (!isSelecting) return false

        var sX = selectionStartX
        var sY = selectionStartY
        var eX = selectionEndX
        var eY = selectionEndY

        // Normalize
        if (sY > eY || (sY == eY && sX > eX)) {
            val tmpX = sX
            val tmpY = sY
            sX = eX
            sY = eY
            eX = tmpX
            eY = tmpY
        }

        return when {
            y < sY || y > eY -> false
            y == sY && y == eY -> x in sX..eX
            y == sY -> x >= sX
            y == eY -> x <= eX
            else -> true
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        // Process scale gesture first
        scaleGestureDetector.onTouchEvent(event)

        // Don't process other gestures while scaling
        if (!isScaling) {
            gestureDetector.onTouchEvent(event)
        }

        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                scrollAccumulator = 0f
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                scrollAccumulator = 0f
            }
            MotionEvent.ACTION_MOVE -> {
                if (isSelecting && !isScaling) {
                    val col = ((event.x - paddingLeft) / charWidth).toInt().coerceIn(0, (emulator?.getColumns() ?: 1) - 1)
                    val row = ((event.y - paddingTop) / charHeight).toInt().coerceIn(0, (emulator?.getRows() ?: 1) - 1)
                    selectionEndX = col
                    selectionEndY = row
                    invalidate()
                }
            }
        }

        return true
    }

    fun startSelection(x: Float, y: Float) {
        val col = ((x - paddingLeft) / charWidth).toInt().coerceIn(0, (emulator?.getColumns() ?: 1) - 1)
        val row = ((y - paddingTop) / charHeight).toInt().coerceIn(0, (emulator?.getRows() ?: 1) - 1)
        selectionStartX = col
        selectionStartY = row
        selectionEndX = col
        selectionEndY = row
        isSelecting = true
        invalidate()
    }

    fun clearSelection() {
        isSelecting = false
        invalidate()
    }

    fun hasSelection(): Boolean = isSelecting

    fun getSelectedText(): String? {
        if (!isSelecting) return null
        return emulator?.getSelectedText(selectionStartX, selectionStartY, selectionEndX, selectionEndY)
    }

    // Scrollback control methods
    fun scrollUp(lines: Int = 1) = emulator?.scrollViewUp(lines)
    fun scrollDown(lines: Int = 1) = emulator?.scrollViewDown(lines)
    fun scrollToTop() = emulator?.scrollToTop()
    fun scrollToBottom() = emulator?.scrollToBottom()
    fun isAtBottom(): Boolean = emulator?.isAtBottom() ?: true
    fun clearScrollback() = emulator?.clearScrollback()

    fun showKeyboard() {
        requestFocus()
        val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.showSoftInput(this, InputMethodManager.SHOW_IMPLICIT)
    }

    fun hideKeyboard() {
        val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow(windowToken, 0)
    }

    override fun onCreateInputConnection(outAttrs: EditorInfo): InputConnection {
        outAttrs.inputType = EditorInfo.TYPE_CLASS_TEXT or EditorInfo.TYPE_TEXT_FLAG_NO_SUGGESTIONS
        outAttrs.imeOptions = EditorInfo.IME_FLAG_NO_FULLSCREEN
        return TerminalInputConnection(this, true)
    }

    override fun onCheckIsTextEditor(): Boolean = true

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        // Auto-scroll to bottom when user types
        emulator?.scrollToBottom()

        val input = when (keyCode) {
            KeyEvent.KEYCODE_ENTER -> "\r"
            KeyEvent.KEYCODE_DEL -> "\u007f"
            KeyEvent.KEYCODE_TAB -> "\t"
            KeyEvent.KEYCODE_ESCAPE -> "\u001b"
            KeyEvent.KEYCODE_DPAD_UP -> "\u001b[A"
            KeyEvent.KEYCODE_DPAD_DOWN -> "\u001b[B"
            KeyEvent.KEYCODE_DPAD_RIGHT -> "\u001b[C"
            KeyEvent.KEYCODE_DPAD_LEFT -> "\u001b[D"
            KeyEvent.KEYCODE_MOVE_HOME -> "\u001b[H"
            KeyEvent.KEYCODE_MOVE_END -> "\u001b[F"
            KeyEvent.KEYCODE_PAGE_UP -> "\u001b[5~"
            KeyEvent.KEYCODE_PAGE_DOWN -> "\u001b[6~"
            KeyEvent.KEYCODE_INSERT -> "\u001b[2~"
            KeyEvent.KEYCODE_FORWARD_DEL -> "\u001b[3~"
            KeyEvent.KEYCODE_F1 -> "\u001bOP"
            KeyEvent.KEYCODE_F2 -> "\u001bOQ"
            KeyEvent.KEYCODE_F3 -> "\u001bOR"
            KeyEvent.KEYCODE_F4 -> "\u001bOS"
            KeyEvent.KEYCODE_F5 -> "\u001b[15~"
            KeyEvent.KEYCODE_F6 -> "\u001b[17~"
            KeyEvent.KEYCODE_F7 -> "\u001b[18~"
            KeyEvent.KEYCODE_F8 -> "\u001b[19~"
            KeyEvent.KEYCODE_F9 -> "\u001b[20~"
            KeyEvent.KEYCODE_F10 -> "\u001b[21~"
            KeyEvent.KEYCODE_F11 -> "\u001b[23~"
            KeyEvent.KEYCODE_F12 -> "\u001b[24~"
            else -> {
                if (event.isCtrlPressed && event.unicodeChar != 0) {
                    val ctrlChar = (event.unicodeChar and 0x1f).toChar().toString()
                    onKeyInput?.invoke(ctrlChar)
                    return true
                }
                val char = event.unicodeChar
                if (char != 0) {
                    char.toChar().toString()
                } else {
                    return super.onKeyDown(keyCode, event)
                }
            }
        }
        onKeyInput?.invoke(input)
        return true
    }

    private inner class TerminalInputConnection(view: View, fullEditor: Boolean) : BaseInputConnection(view, fullEditor) {
        override fun commitText(text: CharSequence?, newCursorPosition: Int): Boolean {
            // Auto-scroll to bottom when user types
            emulator?.scrollToBottom()
            text?.toString()?.let { onKeyInput?.invoke(it) }
            return true
        }

        override fun deleteSurroundingText(beforeLength: Int, afterLength: Int): Boolean {
            if (beforeLength > 0) {
                for (i in 0 until beforeLength) {
                    onKeyInput?.invoke("\u007f")
                }
            }
            return true
        }

        override fun sendKeyEvent(event: KeyEvent): Boolean {
            if (event.action == KeyEvent.ACTION_DOWN) {
                return onKeyDown(event.keyCode, event)
            }
            return true
        }
    }
}
