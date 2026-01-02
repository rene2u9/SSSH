package r2u9.SimpleSSH.terminal

import android.content.Context
import android.graphics.Canvas
import android.graphics.Typeface
import android.os.SystemClock
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
import android.view.inputmethod.BaseInputConnection
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import android.view.inputmethod.InputMethodManager
import android.widget.Scroller
import r2u9.SimpleSSH.util.Constants
import kotlin.math.abs

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

    internal var renderer: TerminalRenderer? = null
        private set
    internal var emulator: TerminalEmulator? = null
        private set

    private var textSizeSp = Constants.Terminal.DEFAULT_FONT_SIZE_SP
    private var onKeyInput: ((String) -> Unit)? = null
    private var onDoubleTap: (() -> Unit)? = null
    private var onLongPress: ((Float, Float) -> Unit)? = null
    private var onFontSizeChanged: ((Float) -> Unit)? = null

    private var isScaling = false
    private var scaleFactor = 1f
    private val scroller = Scroller(context)
    private var topRow = 0
    private var scrollRemainder = 0f

    private var cursorVisible = true
    private val cursorBlink = object : Runnable {
        override fun run() {
            cursorVisible = !cursorVisible
            invalidate()
            postDelayed(this, Constants.Time.CURSOR_BLINK_INTERVAL_MS)
        }
    }

    private var textSelectionController: TextSelectionCursorController? = null
    private var combiningAccent = 0
    private var enforceCharBasedInput = false

    private val gestureDetector = GestureDetector(context, object : GestureDetector.SimpleOnGestureListener() {
        override fun onDown(e: MotionEvent) = true

        override fun onSingleTapUp(e: MotionEvent): Boolean {
            val emu = emulator ?: return true
            if (isSelectingText()) { stopTextSelectionMode(); return true }

            if (emu.isMouseTrackingActive() && !e.isFromSource(InputDevice.SOURCE_MOUSE)) {
                renderer?.getColumnAndRow(e.x, e.y, topRow)?.let { pos ->
                    emu.sendMouseEvent(TerminalEmulator.MOUSE_LEFT_BUTTON, pos[0] + 1, pos[1] + 1, true)
                    emu.sendMouseEvent(TerminalEmulator.MOUSE_LEFT_BUTTON, pos[0] + 1, pos[1] + 1, false)
                }
                return true
            }

            if (!emu.isAtBottom()) emu.scrollToBottom() else showKeyboard()
            requestFocus()
            return true
        }

        override fun onDoubleTap(e: MotionEvent): Boolean { onDoubleTap?.invoke(); return true }

        override fun onLongPress(e: MotionEvent) {
            if (isScaling) return
            val emu = emulator ?: return
            if (emu.isMouseTrackingActive()) return
            if (!isSelectingText()) {
                performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                onLongPress?.invoke(e.x, e.y) ?: startTextSelectionMode(e)
            }
        }

        override fun onScroll(e1: MotionEvent?, e2: MotionEvent, distanceX: Float, distanceY: Float): Boolean {
            val emu = emulator ?: return true
            val rend = renderer ?: return true
            if (isSelectingText()) return false

            if (emu.isMouseTrackingActive() && e2.isFromSource(InputDevice.SOURCE_MOUSE)) {
                val pos = rend.getColumnAndRow(e2.x, e2.y, topRow)
                emu.sendMouseEvent(TerminalEmulator.MOUSE_LEFT_BUTTON_MOVED, pos[0] + 1, pos[1] + 1, true)
                return true
            }

            val adjusted = distanceY + scrollRemainder
            val deltaRows = (adjusted / rend.fontLineSpacing).toInt()
            scrollRemainder = adjusted - deltaRows * rend.fontLineSpacing
            doScroll(e2, deltaRows)
            return true
        }

        override fun onFling(e1: MotionEvent?, e2: MotionEvent, velocityX: Float, velocityY: Float): Boolean {
            val emu = emulator ?: return true
            if (isSelectingText() || !scroller.isFinished) return true

            val scale = 0.25f
            scroller.fling(0, if (emu.isMouseTrackingActive()) 0 else topRow, 0, -(velocityY * scale).toInt(),
                0, 0, if (emu.isMouseTrackingActive()) -emu.getRows() / 2 else -emu.getScrollbackSize(),
                if (emu.isMouseTrackingActive()) emu.getRows() / 2 else 0)

            val mouseTracking = emu.isMouseTrackingActive()
            post(object : Runnable {
                private var lastY = 0
                override fun run() {
                    if (mouseTracking != emu.isMouseTrackingActive()) { scroller.abortAnimation(); return }
                    if (scroller.isFinished) return
                    scroller.computeScrollOffset()
                    doScroll(e2, if (mouseTracking) scroller.currY - lastY else scroller.currY - topRow)
                    lastY = scroller.currY
                    if (!scroller.isFinished) post(this)
                }
            })
            return true
        }
    })

    private val scaleGestureDetector = ScaleGestureDetector(context, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
        override fun onScaleBegin(detector: ScaleGestureDetector) = true.also { isScaling = true }
        override fun onScale(detector: ScaleGestureDetector): Boolean {
            scaleFactor *= detector.scaleFactor
            val newSize = (textSizeSp * scaleFactor).coerceIn(Constants.Terminal.MIN_FONT_SIZE_SP, Constants.Terminal.MAX_FONT_SIZE_SP)
            if (newSize != textSizeSp) { setTextSize(newSize); onFontSizeChanged?.invoke(newSize) }
            return true
        }
        override fun onScaleEnd(detector: ScaleGestureDetector) { isScaling = false; scaleFactor = 1f }
    })

    init {
        isFocusable = true
        isFocusableInTouchMode = true
        updateRenderer()
    }

    private fun updateRenderer() {
        renderer = TerminalRenderer(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, textSizeSp, context.resources.displayMetrics).toInt())
    }

    // Public API
    fun setTextSize(sizeSp: Float) { textSizeSp = sizeSp; updateRenderer(); requestLayout(); invalidate() }
    fun getTextSize() = textSizeSp

    fun setEmulator(emulator: TerminalEmulator) {
        this.emulator = emulator
        emulator.onScreenUpdate = { post { invalidate() } }
        topRow = 0
        requestLayout()
        invalidate()
    }

    fun setOnKeyInput(listener: (String) -> Unit) { onKeyInput = listener }
    fun setOnDoubleTapListener(listener: () -> Unit) { onDoubleTap = listener }
    fun setOnLongPressListener(listener: (Float, Float) -> Unit) { onLongPress = listener }
    fun setOnFontSizeChangedListener(listener: (Float) -> Unit) { onFontSizeChanged = listener }
    fun setEnforceCharBasedInput(enforce: Boolean) { enforceCharBasedInput = enforce }

    // Selection
    fun isSelectingText() = textSelectionController?.isActive() == true
    fun startTextSelectionMode(event: MotionEvent) {
        if (!requestFocus()) return
        if (textSelectionController == null) textSelectionController = TextSelectionCursorController(this)
        textSelectionController?.show(event)
        invalidate()
    }
    fun stopTextSelectionMode() { if (textSelectionController?.hide() == true) invalidate() }
    fun getSelectedText() = textSelectionController?.getSelectedText()
    fun hasSelection() = isSelectingText()
    fun startSelection(x: Float, y: Float) {
        MotionEvent.obtain(SystemClock.uptimeMillis(), SystemClock.uptimeMillis(), MotionEvent.ACTION_DOWN, x, y, 0)
            .also { startTextSelectionMode(it); it.recycle() }
    }
    fun clearSelection() = stopTextSelectionMode()

    fun onPaste(text: String) {
        emulator?.paste(text)?.let { onKeyInput?.invoke(it) }
    }

    // Scrolling
    fun scrollUp(lines: Int = 1) = emulator?.scrollViewUp(lines)
    fun scrollDown(lines: Int = 1) = emulator?.scrollViewDown(lines)
    fun scrollToTop() = emulator?.scrollToTop()
    fun scrollToBottom() = emulator?.scrollToBottom()
    fun isAtBottom() = emulator?.isAtBottom() ?: true
    fun clearScrollback() = emulator?.clearScrollback()

    private fun doScroll(event: MotionEvent, rowsDown: Int) {
        val emu = emulator ?: return
        val up = rowsDown < 0
        repeat(abs(rowsDown)) {
            when {
                emu.isMouseTrackingActive() -> renderer?.getColumnAndRow(event.x, event.y, topRow)?.let { pos ->
                    emu.sendMouseEvent(if (up) TerminalEmulator.MOUSE_WHEELUP_BUTTON else TerminalEmulator.MOUSE_WHEELDOWN_BUTTON, pos[0] + 1, pos[1] + 1, true)
                }
                emu.isAlternateBufferActive() -> onKeyInput?.invoke(if (up) "\u001b[A" else "\u001b[B")
                else -> if (up) emu.scrollViewUp(1) else emu.scrollViewDown(1)
            }
        }
    }

    // Keyboard
    fun showKeyboard() {
        requestFocus()
        (context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager).showSoftInput(this, InputMethodManager.SHOW_IMPLICIT)
    }

    fun hideKeyboard() {
        (context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager).hideSoftInputFromWindow(windowToken, 0)
    }

    // Lifecycle
    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        post(cursorBlink)
        textSelectionController?.let { viewTreeObserver.addOnTouchModeChangeListener(it) }
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        removeCallbacks(cursorBlink)
        textSelectionController?.let {
            stopTextSelectionMode()
            viewTreeObserver.removeOnTouchModeChangeListener(it)
            it.onDetached()
        }
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val emu = emulator
        val rend = renderer
        if (emu != null && rend != null) {
            setMeasuredDimension(
                resolveSize((emu.getColumns() * rend.fontWidth).toInt() + paddingLeft + paddingRight, widthMeasureSpec),
                resolveSize(emu.getRows() * rend.fontLineSpacing + paddingTop + paddingBottom, heightMeasureSpec)
            )
        } else super.onMeasure(widthMeasureSpec, heightMeasureSpec)
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        renderer?.let { rend ->
            emulator?.resize(
                ((w - paddingLeft - paddingRight) / rend.fontWidth).toInt().coerceAtLeast(1),
                ((h - paddingTop - paddingBottom) / rend.fontLineSpacing).coerceAtLeast(1)
            )
        }
    }

    override fun onDraw(canvas: Canvas) {
        val emu = emulator ?: return
        val rend = renderer ?: return
        val sel = textSelectionController?.getSelectors() ?: intArrayOf(-1, -1, -1, -1)
        rend.render(emu, canvas, topRow, sel[2], sel[0], sel[3], sel[1], cursorVisible)
        textSelectionController?.render()
    }

    // Touch
    override fun onTouchEvent(event: MotionEvent): Boolean {
        val emu = emulator ?: return true
        scaleGestureDetector.onTouchEvent(event)

        if (isSelectingText()) { gestureDetector.onTouchEvent(event); return true }

        if (event.isFromSource(InputDevice.SOURCE_MOUSE)) {
            when {
                event.isButtonPressed(MotionEvent.BUTTON_SECONDARY) -> { if (event.action == MotionEvent.ACTION_DOWN) showContextMenu(); return true }
                event.isButtonPressed(MotionEvent.BUTTON_TERTIARY) -> return true
                emu.isMouseTrackingActive() -> {
                    val pos = renderer?.getColumnAndRow(event.x, event.y, topRow) ?: return true
                    when (event.action) {
                        MotionEvent.ACTION_DOWN, MotionEvent.ACTION_UP ->
                            emu.sendMouseEvent(TerminalEmulator.MOUSE_LEFT_BUTTON, pos[0] + 1, pos[1] + 1, event.action == MotionEvent.ACTION_DOWN)
                        MotionEvent.ACTION_MOVE ->
                            emu.sendMouseEvent(TerminalEmulator.MOUSE_LEFT_BUTTON_MOVED, pos[0] + 1, pos[1] + 1, true)
                    }
                    return true
                }
            }
        }

        if (!isScaling) gestureDetector.onTouchEvent(event)
        if (event.action in listOf(MotionEvent.ACTION_DOWN, MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL)) scrollRemainder = 0f
        return true
    }

    override fun onGenericMotionEvent(event: MotionEvent): Boolean {
        if (event.isFromSource(InputDevice.SOURCE_MOUSE) && event.action == MotionEvent.ACTION_SCROLL) {
            doScroll(event, if (event.getAxisValue(MotionEvent.AXIS_VSCROLL) > 0f) -3 else 3)
            return true
        }
        return false
    }

    // Input connection
    override fun onCreateInputConnection(outAttrs: EditorInfo): InputConnection {
        outAttrs.inputType = if (enforceCharBasedInput) InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS else InputType.TYPE_NULL
        outAttrs.imeOptions = EditorInfo.IME_FLAG_NO_FULLSCREEN

        return object : BaseInputConnection(this, true) {
            override fun finishComposingText() = true.also { editable?.let { sendText(it); it.clear() } }
            override fun commitText(text: CharSequence?, newCursorPosition: Int) = super.commitText(text, newCursorPosition).also {
                if (emulator != null) editable?.let { sendText(it); it.clear() }
            }
            override fun deleteSurroundingText(beforeLength: Int, afterLength: Int): Boolean {
                repeat(beforeLength) { sendKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DEL)) }
                return super.deleteSurroundingText(beforeLength, afterLength)
            }

            private fun sendText(text: CharSequence) {
                stopTextSelectionMode()
                var i = 0
                while (i < text.length) {
                    val first = text[i]
                    var cp = if (first.isHighSurrogate() && ++i < text.length) Character.toCodePoint(first, text[i]) else first.code
                    if (cp <= 31 && cp != 27 && cp == '\n'.code) cp = '\r'.code
                    inputCodePoint(0, cp, false, false)
                    i++
                }
            }
        }
    }

    override fun onCheckIsTextEditor() = true

    // Key handling
    override fun onKeyPreIme(keyCode: Int, event: KeyEvent): Boolean {
        if (keyCode == KeyEvent.KEYCODE_BACK && isSelectingText()) { stopTextSelectionMode(); return true }
        return super.onKeyPreIme(keyCode, event)
    }

    @Suppress("DEPRECATION")
    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        val emu = emulator ?: return true
        if (isSelectingText()) stopTextSelectionMode()
        emu.scrollToBottom()

        if (event.isSystem && keyCode != KeyEvent.KEYCODE_BACK) return super.onKeyDown(keyCode, event)
        if (event.action == KeyEvent.ACTION_MULTIPLE && keyCode == KeyEvent.KEYCODE_UNKNOWN) {
            event.characters?.let { onKeyInput?.invoke(it) }
            return true
        }

        val meta = event.metaState
        val ctrl = event.isCtrlPressed
        val leftAlt = (meta and KeyEvent.META_ALT_LEFT_ON) != 0
        val shift = event.isShiftPressed
        val rightAlt = (meta and KeyEvent.META_ALT_RIGHT_ON) != 0

        var keyMod = 0
        if (ctrl) keyMod = keyMod or 1
        if (event.isAltPressed || leftAlt) keyMod = keyMod or 2
        if (shift) keyMod = keyMod or 4

        if (!event.isFunctionPressed && handleKeyCode(keyCode, keyMod, emu)) return true

        var bitsToClear = KeyEvent.META_CTRL_MASK
        if (!rightAlt) bitsToClear = bitsToClear or KeyEvent.META_ALT_ON or KeyEvent.META_ALT_LEFT_ON
        var effectiveMeta = meta and bitsToClear.inv()
        if (shift) effectiveMeta = effectiveMeta or KeyEvent.META_SHIFT_ON or KeyEvent.META_SHIFT_LEFT_ON

        val result = event.getUnicodeChar(effectiveMeta)
        if (result == 0) return false

        val oldAccent = combiningAccent
        if ((result and KeyCharacterMap.COMBINING_ACCENT) != 0) {
            if (combiningAccent != 0) inputCodePoint(event.deviceId, combiningAccent, ctrl, leftAlt)
            combiningAccent = result and KeyCharacterMap.COMBINING_ACCENT_MASK
        } else {
            var cp = result
            if (combiningAccent != 0) {
                val combined = KeyCharacterMap.getDeadChar(combiningAccent, result)
                if (combined > 0) cp = combined
                combiningAccent = 0
            }
            inputCodePoint(event.deviceId, cp, ctrl, leftAlt)
        }
        if (combiningAccent != oldAccent) invalidate()
        return true
    }

    override fun onKeyUp(keyCode: Int, event: KeyEvent): Boolean {
        if (emulator == null && keyCode != KeyEvent.KEYCODE_BACK) return true
        return if (event.isSystem) super.onKeyUp(keyCode, event) else true
    }

    private fun inputCodePoint(src: Int, codePoint: Int, ctrl: Boolean, alt: Boolean) {
        var cp = codePoint
        if (ctrl) cp = when {
            cp in 'a'.code..'z'.code -> cp - 'a'.code + 1
            cp in 'A'.code..'Z'.code -> cp - 'A'.code + 1
            cp == ' '.code || cp == '2'.code -> 0
            cp == '['.code || cp == '3'.code -> 27
            cp == '\\'.code || cp == '4'.code -> 28
            cp == ']'.code || cp == '5'.code -> 29
            cp == '^'.code || cp == '6'.code -> 30
            cp == '_'.code || cp == '7'.code || cp == '/'.code -> 31
            cp == '8'.code -> 127
            else -> cp
        }
        if (cp > -1) {
            if (src > 0) cp = when (cp) { 0x02DC -> 0x007E; 0x02CB -> 0x0060; 0x02C6 -> 0x005E; else -> cp }
            onKeyInput?.invoke(if (alt) "\u001b${Character.toChars(cp).concatToString()}" else Character.toChars(cp).concatToString())
        }
    }

    private fun handleKeyCode(keyCode: Int, keyMod: Int, emu: TerminalEmulator): Boolean {
        val app = emu.isApplicationCursorKeys()
        val code = when (keyCode) {
            KeyEvent.KEYCODE_DPAD_UP -> if (app) "\u001bOA" else "\u001b[A"
            KeyEvent.KEYCODE_DPAD_DOWN -> if (app) "\u001bOB" else "\u001b[B"
            KeyEvent.KEYCODE_DPAD_RIGHT -> if (app) "\u001bOC" else "\u001b[C"
            KeyEvent.KEYCODE_DPAD_LEFT -> if (app) "\u001bOD" else "\u001b[D"
            KeyEvent.KEYCODE_MOVE_HOME -> "\u001b[H"
            KeyEvent.KEYCODE_MOVE_END -> "\u001b[F"
            KeyEvent.KEYCODE_PAGE_UP -> if ((keyMod and 4) != 0) { doScroll(MotionEvent.obtain(0, 0, 0, 0f, 0f, 0), -emu.getRows()); return true } else "\u001b[5~"
            KeyEvent.KEYCODE_PAGE_DOWN -> if ((keyMod and 4) != 0) { doScroll(MotionEvent.obtain(0, 0, 0, 0f, 0f, 0), emu.getRows()); return true } else "\u001b[6~"
            KeyEvent.KEYCODE_INSERT -> "\u001b[2~"
            KeyEvent.KEYCODE_FORWARD_DEL -> "\u001b[3~"
            KeyEvent.KEYCODE_TAB -> if ((keyMod and 4) != 0) "\u001b[Z" else "\t"
            KeyEvent.KEYCODE_ENTER -> "\r"
            KeyEvent.KEYCODE_DEL -> "\u007f"
            KeyEvent.KEYCODE_ESCAPE -> "\u001b"
            KeyEvent.KEYCODE_F1 -> "\u001bOP"; KeyEvent.KEYCODE_F2 -> "\u001bOQ"; KeyEvent.KEYCODE_F3 -> "\u001bOR"; KeyEvent.KEYCODE_F4 -> "\u001bOS"
            KeyEvent.KEYCODE_F5 -> "\u001b[15~"; KeyEvent.KEYCODE_F6 -> "\u001b[17~"; KeyEvent.KEYCODE_F7 -> "\u001b[18~"; KeyEvent.KEYCODE_F8 -> "\u001b[19~"
            KeyEvent.KEYCODE_F9 -> "\u001b[20~"; KeyEvent.KEYCODE_F10 -> "\u001b[21~"; KeyEvent.KEYCODE_F11 -> "\u001b[23~"; KeyEvent.KEYCODE_F12 -> "\u001b[24~"
            else -> return false
        }
        onKeyInput?.invoke(code)
        return true
    }
}
