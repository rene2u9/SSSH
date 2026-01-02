package r2u9.SimpleSSH.terminal

import android.content.Context
import android.graphics.Canvas
import android.graphics.Typeface
import android.os.Build
import android.text.InputType
import android.util.AttributeSet
import android.util.TypedValue
import android.view.GestureDetector
import android.view.HapticFeedbackConstants
import android.view.InputDevice
import android.view.KeyCharacterMap
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import android.view.ViewTreeObserver
import android.view.inputmethod.BaseInputConnection
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import android.view.inputmethod.InputMethodManager
import android.widget.Scroller
import r2u9.SimpleSSH.util.Constants

/**
 * Custom view for rendering terminal content with support for:
 * - Text run batching for performance
 * - Draggable selection handles
 * - Mouse event reporting
 * - Wide character (CJK) support
 * - Various keyboard workarounds
 */
class TerminalView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    // Renderer
    internal var renderer: TerminalRenderer? = null
        private set

    // Emulator
    internal var emulator: TerminalEmulator? = null
        private set

    // Text size
    private var textSizeSp: Float = Constants.Terminal.DEFAULT_FONT_SIZE_SP

    // Callbacks
    private var onKeyInput: ((String) -> Unit)? = null
    private var onDoubleTap: (() -> Unit)? = null
    private var onLongPress: ((Float, Float) -> Unit)? = null
    private var onFontSizeChanged: ((Float) -> Unit)? = null

    // Scaling
    private var isScaling = false
    private var scaleFactor = 1f

    // Scrolling
    private val scroller = Scroller(context)
    private var topRow = 0
    private var scrollRemainder = 0f

    // Cursor blinking
    private var cursorVisible = true
    private val cursorBlink = object : Runnable {
        override fun run() {
            cursorVisible = !cursorVisible
            invalidate()
            postDelayed(this, Constants.Time.CURSOR_BLINK_INTERVAL_MS)
        }
    }

    // Text selection
    private var textSelectionController: TextSelectionCursorController? = null

    // Mouse tracking
    private var mouseScrollStartX = -1
    private var mouseScrollStartY = -1
    private var mouseStartDownTime = -1L

    // Combining accents (for international keyboards)
    private var combiningAccent = 0

    // Keyboard workarounds
    private var enforceCharBasedInput = false

    // Gesture recognizers
    private val gestureDetector = GestureDetector(context, object : GestureDetector.SimpleOnGestureListener() {
        private var scrolledWithFinger = false

        override fun onDown(e: MotionEvent): Boolean {
            scrolledWithFinger = false
            return true
        }

        override fun onSingleTapUp(e: MotionEvent): Boolean {
            val emu = emulator ?: return true

            if (isSelectingText()) {
                stopTextSelectionMode()
                return true
            }

            // If mouse tracking is active, send mouse event
            if (emu.isMouseTrackingActive() && !e.isFromSource(InputDevice.SOURCE_MOUSE)) {
                val pos = renderer?.getColumnAndRow(e.x, e.y, topRow) ?: return true
                emu.sendMouseEvent(TerminalEmulator.MOUSE_LEFT_BUTTON, pos[0] + 1, pos[1] + 1, true)
                emu.sendMouseEvent(TerminalEmulator.MOUSE_LEFT_BUTTON, pos[0] + 1, pos[1] + 1, false)
                return true
            }

            if (!emu.isAtBottom()) {
                emu.scrollToBottom()
            } else {
                showKeyboard()
            }
            requestFocus()
            return true
        }

        override fun onDoubleTap(e: MotionEvent): Boolean {
            onDoubleTap?.invoke()
            return true
        }

        override fun onLongPress(e: MotionEvent) {
            if (isScaling) return

            val emu = emulator ?: return
            if (emu.isMouseTrackingActive()) return

            onLongPress?.invoke(e.x, e.y)

            if (!isSelectingText()) {
                performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                startTextSelectionMode(e)
            }
        }

        override fun onScroll(e1: MotionEvent?, e2: MotionEvent, distanceX: Float, distanceY: Float): Boolean {
            val emu = emulator ?: return true
            val rend = renderer ?: return true

            if (isSelectingText()) return false

            // Mouse tracking with mouse device
            if (emu.isMouseTrackingActive() && e2.isFromSource(InputDevice.SOURCE_MOUSE)) {
                val pos = rend.getColumnAndRow(e2.x, e2.y, topRow)
                emu.sendMouseEvent(TerminalEmulator.MOUSE_LEFT_BUTTON_MOVED, pos[0] + 1, pos[1] + 1, true)
                return true
            }

            scrolledWithFinger = true
            val adjustedDistance = distanceY + scrollRemainder
            val deltaRows = (adjustedDistance / rend.fontLineSpacing).toInt()
            scrollRemainder = adjustedDistance - deltaRows * rend.fontLineSpacing
            doScroll(e2, deltaRows)
            return true
        }

        override fun onFling(e1: MotionEvent?, e2: MotionEvent, velocityX: Float, velocityY: Float): Boolean {
            val emu = emulator ?: return true

            if (isSelectingText()) return false
            if (!scroller.isFinished) return true

            val scale = 0.25f
            if (emu.isMouseTrackingActive()) {
                scroller.fling(0, 0, 0, -(velocityY * scale).toInt(), 0, 0, -emu.getRows() / 2, emu.getRows() / 2)
            } else {
                scroller.fling(0, topRow, 0, -(velocityY * scale).toInt(), 0, 0, -emu.getScrollbackSize(), 0)
            }

            val mouseTrackingAtStart = emu.isMouseTrackingActive()
            post(object : Runnable {
                private var lastY = 0

                override fun run() {
                    if (mouseTrackingAtStart != emu.isMouseTrackingActive()) {
                        scroller.abortAnimation()
                        return
                    }
                    if (scroller.isFinished) return
                    val more = scroller.computeScrollOffset()
                    val newY = scroller.currY
                    val diff = if (mouseTrackingAtStart) newY - lastY else newY - topRow
                    doScroll(e2, diff)
                    lastY = newY
                    if (more) post(this)
                }
            })
            return true
        }
    })

    private val scaleGestureDetector = ScaleGestureDetector(context, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
        override fun onScaleBegin(detector: ScaleGestureDetector): Boolean {
            isScaling = true
            return true
        }

        override fun onScale(detector: ScaleGestureDetector): Boolean {
            scaleFactor *= detector.scaleFactor
            val newSize = (textSizeSp * scaleFactor).coerceIn(
                Constants.Terminal.MIN_FONT_SIZE_SP,
                Constants.Terminal.MAX_FONT_SIZE_SP
            )
            if (newSize != textSizeSp) {
                setTextSize(newSize)
                onFontSizeChanged?.invoke(newSize)
            }
            return true
        }

        override fun onScaleEnd(detector: ScaleGestureDetector) {
            isScaling = false
            scaleFactor = 1f
        }
    })

    init {
        isFocusable = true
        isFocusableInTouchMode = true
        updateRenderer()
    }

    private fun updateRenderer() {
        val textSizePx = spToPx(textSizeSp).toInt()
        renderer = TerminalRenderer(textSizePx, Typeface.MONOSPACE)
    }

    private fun spToPx(sp: Float): Float {
        return TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_SP,
            sp,
            context.resources.displayMetrics
        )
    }

    // Public API

    fun setTextSize(sizeSp: Float) {
        textSizeSp = sizeSp
        updateRenderer()
        requestLayout()
        invalidate()
    }

    fun getTextSize(): Float = textSizeSp

    fun setEmulator(emulator: TerminalEmulator) {
        this.emulator = emulator
        emulator.onScreenUpdate = {
            post { invalidate() }
        }
        topRow = 0
        requestLayout()
        invalidate()
    }

    fun setOnKeyInput(listener: (String) -> Unit) {
        onKeyInput = listener
    }

    fun setOnDoubleTapListener(listener: () -> Unit) {
        onDoubleTap = listener
    }

    fun setOnLongPressListener(listener: (Float, Float) -> Unit) {
        onLongPress = listener
    }

    fun setOnFontSizeChangedListener(listener: (Float) -> Unit) {
        onFontSizeChanged = listener
    }

    /**
     * Enable or disable character-based input mode.
     * Some keyboards (like Samsung) work better with this enabled.
     */
    fun setEnforceCharBasedInput(enforce: Boolean) {
        enforceCharBasedInput = enforce
    }

    // Text selection

    fun isSelectingText(): Boolean = textSelectionController?.isActive() == true

    fun startTextSelectionMode(event: MotionEvent) {
        if (!requestFocus()) return

        if (textSelectionController == null) {
            textSelectionController = TextSelectionCursorController(this)
        }
        textSelectionController?.show(event)
        invalidate()
    }

    fun stopTextSelectionMode() {
        if (textSelectionController?.hide() == true) {
            invalidate()
        }
    }

    fun getSelectedText(): String? = textSelectionController?.getSelectedText()

    /**
     * Check if there's an active selection.
     */
    fun hasSelection(): Boolean = isSelectingText()

    /**
     * Start text selection at a specific position.
     * Creates a synthetic motion event and starts selection mode.
     */
    fun startSelection(x: Float, y: Float) {
        val event = android.view.MotionEvent.obtain(
            android.os.SystemClock.uptimeMillis(),
            android.os.SystemClock.uptimeMillis(),
            android.view.MotionEvent.ACTION_DOWN,
            x, y, 0
        )
        startTextSelectionMode(event)
        event.recycle()
    }

    /**
     * Clear the current text selection.
     */
    fun clearSelection() {
        stopTextSelectionMode()
    }

    /**
     * Called when text is pasted from clipboard or selection.
     */
    fun onPaste(text: String) {
        val emu = emulator ?: return
        val pasteText = emu.paste(text)
        onKeyInput?.invoke(pasteText)
    }

    // Scrolling

    fun scrollUp(lines: Int = 1) = emulator?.scrollViewUp(lines)
    fun scrollDown(lines: Int = 1) = emulator?.scrollViewDown(lines)
    fun scrollToTop() = emulator?.scrollToTop()
    fun scrollToBottom() = emulator?.scrollToBottom()
    fun isAtBottom(): Boolean = emulator?.isAtBottom() ?: true
    fun clearScrollback() = emulator?.clearScrollback()

    private fun doScroll(event: MotionEvent, rowsDown: Int) {
        val emu = emulator ?: return
        val up = rowsDown < 0
        val amount = kotlin.math.abs(rowsDown)

        for (i in 0 until amount) {
            if (emu.isMouseTrackingActive()) {
                val pos = renderer?.getColumnAndRow(event.x, event.y, topRow) ?: continue
                val button = if (up) TerminalEmulator.MOUSE_WHEELUP_BUTTON else TerminalEmulator.MOUSE_WHEELDOWN_BUTTON
                emu.sendMouseEvent(button, pos[0] + 1, pos[1] + 1, true)
            } else if (emu.isAlternateBufferActive()) {
                // In alternate buffer, send arrow keys for scrolling (like less)
                val key = if (up) "\u001b[A" else "\u001b[B"
                onKeyInput?.invoke(key)
            } else {
                if (up) {
                    emu.scrollViewUp(1)
                } else {
                    emu.scrollViewDown(1)
                }
            }
        }
    }

    // Keyboard

    fun showKeyboard() {
        requestFocus()
        val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.showSoftInput(this, InputMethodManager.SHOW_IMPLICIT)
    }

    fun hideKeyboard() {
        val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow(windowToken, 0)
    }

    // View lifecycle

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        post(cursorBlink)

        if (textSelectionController != null) {
            viewTreeObserver.addOnTouchModeChangeListener(textSelectionController)
        }
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        removeCallbacks(cursorBlink)

        if (textSelectionController != null) {
            stopTextSelectionMode()
            viewTreeObserver.removeOnTouchModeChangeListener(textSelectionController)
            textSelectionController?.onDetached()
        }
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val emu = emulator
        val rend = renderer

        if (emu != null && rend != null) {
            val desiredWidth = (emu.getColumns() * rend.fontWidth).toInt() + paddingLeft + paddingRight
            val desiredHeight = (emu.getRows() * rend.fontLineSpacing) + paddingTop + paddingBottom

            val width = resolveSize(desiredWidth, widthMeasureSpec)
            val height = resolveSize(desiredHeight, heightMeasureSpec)
            setMeasuredDimension(width, height)
        } else {
            super.onMeasure(widthMeasureSpec, heightMeasureSpec)
        }
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        val rend = renderer ?: return

        val cols = ((w - paddingLeft - paddingRight) / rend.fontWidth).toInt().coerceAtLeast(1)
        val rows = ((h - paddingTop - paddingBottom) / rend.fontLineSpacing).coerceAtLeast(1)
        emulator?.resize(cols, rows)
    }

    override fun onDraw(canvas: Canvas) {
        val emu = emulator ?: return
        val rend = renderer ?: return

        // Get selection bounds if selecting
        val sel = if (isSelectingText()) {
            textSelectionController?.getSelectors() ?: intArrayOf(-1, -1, -1, -1)
        } else {
            intArrayOf(-1, -1, -1, -1)
        }

        // Render using the batched renderer
        rend.render(
            emu, canvas, topRow,
            sel[2], sel[0], sel[3], sel[1],
            TerminalRenderer.CursorStyle.BLOCK
        )

        // Render selection handles
        textSelectionController?.render()
    }

    // Touch events

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val emu = emulator ?: return true

        // Handle scaling first
        scaleGestureDetector.onTouchEvent(event)

        if (isSelectingText()) {
            gestureDetector.onTouchEvent(event)
            return true
        }

        // Handle mouse input from actual mouse device
        if (event.isFromSource(InputDevice.SOURCE_MOUSE)) {
            when {
                event.isButtonPressed(MotionEvent.BUTTON_SECONDARY) -> {
                    if (event.action == MotionEvent.ACTION_DOWN) {
                        showContextMenu()
                    }
                    return true
                }
                event.isButtonPressed(MotionEvent.BUTTON_TERTIARY) -> {
                    // Middle click paste - handled elsewhere
                    return true
                }
                emu.isMouseTrackingActive() -> {
                    val pos = renderer?.getColumnAndRow(event.x, event.y, topRow) ?: return true
                    when (event.action) {
                        MotionEvent.ACTION_DOWN, MotionEvent.ACTION_UP -> {
                            emu.sendMouseEvent(
                                TerminalEmulator.MOUSE_LEFT_BUTTON,
                                pos[0] + 1, pos[1] + 1,
                                event.action == MotionEvent.ACTION_DOWN
                            )
                        }
                        MotionEvent.ACTION_MOVE -> {
                            emu.sendMouseEvent(
                                TerminalEmulator.MOUSE_LEFT_BUTTON_MOVED,
                                pos[0] + 1, pos[1] + 1, true
                            )
                        }
                    }
                    return true
                }
            }
        }

        if (!isScaling) {
            gestureDetector.onTouchEvent(event)
        }

        when (event.action) {
            MotionEvent.ACTION_DOWN -> scrollRemainder = 0f
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> scrollRemainder = 0f
        }

        return true
    }

    override fun onGenericMotionEvent(event: MotionEvent): Boolean {
        val emu = emulator ?: return false

        if (event.isFromSource(InputDevice.SOURCE_MOUSE) && event.action == MotionEvent.ACTION_SCROLL) {
            val up = event.getAxisValue(MotionEvent.AXIS_VSCROLL) > 0f
            doScroll(event, if (up) -3 else 3)
            return true
        }
        return false
    }

    // Input connection

    override fun onCreateInputConnection(outAttrs: EditorInfo): InputConnection {
        if (enforceCharBasedInput) {
            // Workaround for Samsung keyboards and some others
            outAttrs.inputType = InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
        } else {
            outAttrs.inputType = InputType.TYPE_NULL
        }
        outAttrs.imeOptions = EditorInfo.IME_FLAG_NO_FULLSCREEN

        return object : BaseInputConnection(this, true) {
            override fun finishComposingText(): Boolean {
                super.finishComposingText()
                editable?.let { sendTextToTerminal(it) }
                editable?.clear()
                return true
            }

            override fun commitText(text: CharSequence?, newCursorPosition: Int): Boolean {
                super.commitText(text, newCursorPosition)
                if (emulator == null) return true
                editable?.let { sendTextToTerminal(it) }
                editable?.clear()
                return true
            }

            override fun deleteSurroundingText(beforeLength: Int, afterLength: Int): Boolean {
                // Samsung keyboards send leftLength > 1
                val deleteKey = KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DEL)
                for (i in 0 until beforeLength) {
                    sendKeyEvent(deleteKey)
                }
                return super.deleteSurroundingText(beforeLength, afterLength)
            }

            private fun sendTextToTerminal(text: CharSequence) {
                stopTextSelectionMode()
                var i = 0
                while (i < text.length) {
                    val firstChar = text[i]
                    val codePoint = if (Character.isHighSurrogate(firstChar)) {
                        if (++i < text.length) {
                            Character.toCodePoint(firstChar, text[i])
                        } else {
                            0xFFFD // Replacement character
                        }
                    } else {
                        firstChar.code
                    }

                    var cp = codePoint

                    // Handle control characters
                    if (cp <= 31 && cp != 27) {
                        if (cp == '\n'.code) {
                            cp = '\r'.code
                        }
                    }

                    inputCodePoint(0, cp, false, false)
                    i++
                }
            }
        }
    }

    override fun onCheckIsTextEditor(): Boolean = true

    // Key handling

    override fun onKeyPreIme(keyCode: Int, event: KeyEvent): Boolean {
        if (keyCode == KeyEvent.KEYCODE_BACK && isSelectingText()) {
            stopTextSelectionMode()
            return true
        }
        return super.onKeyPreIme(keyCode, event)
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        val emu = emulator ?: return true

        if (isSelectingText()) {
            stopTextSelectionMode()
        }

        // Scroll to bottom on key press
        emu.scrollToBottom()

        // Handle system keys
        if (event.isSystem && keyCode != KeyEvent.KEYCODE_BACK) {
            return super.onKeyDown(keyCode, event)
        }

        // Handle multiple characters
        if (event.action == KeyEvent.ACTION_MULTIPLE && keyCode == KeyEvent.KEYCODE_UNKNOWN) {
            event.characters?.let { onKeyInput?.invoke(it) }
            return true
        }

        val metaState = event.metaState
        val controlDown = event.isCtrlPressed
        val leftAltDown = (metaState and KeyEvent.META_ALT_LEFT_ON) != 0
        val shiftDown = event.isShiftPressed
        val rightAltDown = (metaState and KeyEvent.META_ALT_RIGHT_ON) != 0

        // Build key modifier mask
        var keyMod = 0
        if (controlDown) keyMod = keyMod or KEYMOD_CTRL
        if (event.isAltPressed || leftAltDown) keyMod = keyMod or KEYMOD_ALT
        if (shiftDown) keyMod = keyMod or KEYMOD_SHIFT
        if (event.isNumLockOn) keyMod = keyMod or KEYMOD_NUM_LOCK

        // Handle special keys
        if (!event.isFunctionPressed && handleKeyCode(keyCode, keyMod, emu)) {
            return true
        }

        // Get unicode character
        var bitsToClear = KeyEvent.META_CTRL_MASK
        if (!rightAltDown) {
            bitsToClear = bitsToClear or KeyEvent.META_ALT_ON or KeyEvent.META_ALT_LEFT_ON
        }
        var effectiveMetaState = metaState and bitsToClear.inv()
        if (shiftDown) effectiveMetaState = effectiveMetaState or KeyEvent.META_SHIFT_ON or KeyEvent.META_SHIFT_LEFT_ON

        val result = event.getUnicodeChar(effectiveMetaState)
        if (result == 0) return false

        val oldCombiningAccent = combiningAccent
        if ((result and KeyCharacterMap.COMBINING_ACCENT) != 0) {
            if (combiningAccent != 0) {
                inputCodePoint(event.deviceId, combiningAccent, controlDown, leftAltDown)
            }
            combiningAccent = result and KeyCharacterMap.COMBINING_ACCENT_MASK
        } else {
            var codePoint = result
            if (combiningAccent != 0) {
                val combined = KeyCharacterMap.getDeadChar(combiningAccent, result)
                if (combined > 0) codePoint = combined
                combiningAccent = 0
            }
            inputCodePoint(event.deviceId, codePoint, controlDown, leftAltDown)
        }

        if (combiningAccent != oldCombiningAccent) invalidate()

        return true
    }

    override fun onKeyUp(keyCode: Int, event: KeyEvent): Boolean {
        if (emulator == null && keyCode != KeyEvent.KEYCODE_BACK) return true
        if (event.isSystem) return super.onKeyUp(keyCode, event)
        return true
    }

    private fun inputCodePoint(eventSource: Int, codePoint: Int, controlDown: Boolean, leftAltDown: Boolean) {
        var cp = codePoint

        if (controlDown) {
            cp = when {
                cp in 'a'.code..'z'.code -> cp - 'a'.code + 1
                cp in 'A'.code..'Z'.code -> cp - 'A'.code + 1
                cp == ' '.code || cp == '2'.code -> 0
                cp == '['.code || cp == '3'.code -> 27 // ESC
                cp == '\\'.code || cp == '4'.code -> 28
                cp == ']'.code || cp == '5'.code -> 29
                cp == '^'.code || cp == '6'.code -> 30
                cp == '_'.code || cp == '7'.code || cp == '/'.code -> 31
                cp == '8'.code -> 127 // DEL
                else -> cp
            }
        }

        if (cp > -1) {
            // Fix bluetooth keyboard unicode quirks
            if (eventSource > 0) {
                cp = when (cp) {
                    0x02DC -> 0x007E // SMALL TILDE -> TILDE
                    0x02CB -> 0x0060 // MODIFIER LETTER GRAVE -> GRAVE
                    0x02C6 -> 0x005E // MODIFIER CIRCUMFLEX -> CIRCUMFLEX
                    else -> cp
                }
            }

            // Send escape prefix for Alt key
            val text = if (leftAltDown) {
                "\u001b${Character.toChars(cp).concatToString()}"
            } else {
                Character.toChars(cp).concatToString()
            }
            onKeyInput?.invoke(text)
        }
    }

    private fun handleKeyCode(keyCode: Int, keyMod: Int, emu: TerminalEmulator): Boolean {
        val cursorApp = emu.isApplicationCursorKeys()

        val code = when (keyCode) {
            KeyEvent.KEYCODE_DPAD_UP -> if (cursorApp) "\u001bOA" else "\u001b[A"
            KeyEvent.KEYCODE_DPAD_DOWN -> if (cursorApp) "\u001bOB" else "\u001b[B"
            KeyEvent.KEYCODE_DPAD_RIGHT -> if (cursorApp) "\u001bOC" else "\u001b[C"
            KeyEvent.KEYCODE_DPAD_LEFT -> if (cursorApp) "\u001bOD" else "\u001b[D"
            KeyEvent.KEYCODE_MOVE_HOME -> "\u001b[H"
            KeyEvent.KEYCODE_MOVE_END -> "\u001b[F"
            KeyEvent.KEYCODE_PAGE_UP -> {
                if ((keyMod and KEYMOD_SHIFT) != 0) {
                    doScroll(MotionEvent.obtain(0, 0, MotionEvent.ACTION_DOWN, 0f, 0f, 0), -emu.getRows())
                    return true
                }
                "\u001b[5~"
            }
            KeyEvent.KEYCODE_PAGE_DOWN -> {
                if ((keyMod and KEYMOD_SHIFT) != 0) {
                    doScroll(MotionEvent.obtain(0, 0, MotionEvent.ACTION_DOWN, 0f, 0f, 0), emu.getRows())
                    return true
                }
                "\u001b[6~"
            }
            KeyEvent.KEYCODE_INSERT -> "\u001b[2~"
            KeyEvent.KEYCODE_FORWARD_DEL -> "\u001b[3~"
            KeyEvent.KEYCODE_TAB -> if ((keyMod and KEYMOD_SHIFT) != 0) "\u001b[Z" else "\t"
            KeyEvent.KEYCODE_ENTER -> "\r"
            KeyEvent.KEYCODE_DEL -> "\u007f"
            KeyEvent.KEYCODE_ESCAPE -> "\u001b"
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
            else -> return false
        }

        onKeyInput?.invoke(code)
        return true
    }

    companion object {
        private const val KEYMOD_CTRL = 1
        private const val KEYMOD_ALT = 2
        private const val KEYMOD_SHIFT = 4
        private const val KEYMOD_NUM_LOCK = 8
    }
}
