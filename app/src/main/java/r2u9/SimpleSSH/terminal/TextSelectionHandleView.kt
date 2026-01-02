package r2u9.SimpleSSH.terminal

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.drawable.Drawable
import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.PopupWindow
import androidx.core.content.ContextCompat

/**
 * A draggable handle view for text selection in the terminal.
 * Based on Android's text selection handle implementation.
 */
class TextSelectionHandleView(
    private val terminalView: TerminalView,
    private val controller: TextSelectionCursorController,
    private val isLeft: Boolean
) {
    companion object {
        const val LEFT = true
        const val RIGHT = false
        private const val HANDLE_SIZE_DP = 22
        private const val TOUCH_SLOP_DP = 24
    }

    private val context: Context = terminalView.context
    private val handleSizePx: Int
    private val touchSlopPx: Int

    private val popupWindow: PopupWindow
    private val handleView: HandleView

    private var isDragging = false
    private var touchDownX = 0f
    private var touchDownY = 0f
    private var lastX = 0
    private var lastY = 0

    private var positionX = 0
    private var positionY = 0

    init {
        val density = context.resources.displayMetrics.density
        handleSizePx = (HANDLE_SIZE_DP * density).toInt()
        touchSlopPx = (TOUCH_SLOP_DP * density).toInt()

        handleView = HandleView(context, isLeft, handleSizePx)

        popupWindow = PopupWindow(handleView, handleSizePx, handleSizePx).apply {
            isClippingEnabled = false
            isSplitTouchEnabled = true
        }

        handleView.setOnTouchListener { _, event ->
            onTouchEvent(event)
            true
        }
    }

    fun getHandleHeight(): Int = handleSizePx

    fun isDragging(): Boolean = isDragging

    fun positionAtCursor(column: Int, row: Int, forceShow: Boolean) {
        val renderer = terminalView.renderer ?: return

        val x = renderer.getPointX(column)
        val y = renderer.getPointY(row + 1)

        positionX = x
        positionY = y

        val location = IntArray(2)
        terminalView.getLocationOnScreen(location)

        val handleX = location[0] + x - (if (isLeft) handleSizePx else 0)
        val handleY = location[1] + y

        if (forceShow || !popupWindow.isShowing) {
            popupWindow.showAtLocation(terminalView, 0, handleX, handleY)
        } else {
            popupWindow.update(handleX, handleY, -1, -1)
        }
    }

    fun hide() {
        isDragging = false
        if (popupWindow.isShowing) {
            popupWindow.dismiss()
        }
    }

    private fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                touchDownX = event.rawX
                touchDownY = event.rawY
                lastX = positionX
                lastY = positionY
                isDragging = true
            }

            MotionEvent.ACTION_MOVE -> {
                if (isDragging) {
                    val deltaX = event.rawX - touchDownX
                    val deltaY = event.rawY - touchDownY

                    val newX = (lastX + deltaX).toInt()
                    val newY = (lastY + deltaY).toInt()

                    controller.updatePosition(this, newX, newY)
                }
            }

            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                isDragging = false
            }
        }
        return true
    }

    /**
     * Custom view that draws the selection handle.
     */
    private class HandleView(
        context: Context,
        private val isLeft: Boolean,
        private val sizePx: Int
    ) : View(context) {

        private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFF448AFF.toInt() // Material Blue
            style = Paint.Style.FILL
        }

        private val path = Path()

        init {
            // Create the handle shape
            val radius = sizePx / 2f
            val centerX = if (isLeft) sizePx.toFloat() else 0f

            path.reset()
            if (isLeft) {
                // Left handle - circle with stem on right
                path.addCircle(radius, radius * 1.5f, radius, Path.Direction.CW)
                path.moveTo(radius, 0f)
                path.lineTo(sizePx.toFloat(), 0f)
                path.lineTo(sizePx.toFloat(), radius)
                path.lineTo(radius, radius * 0.5f)
                path.close()
            } else {
                // Right handle - circle with stem on left
                path.addCircle(radius, radius * 1.5f, radius, Path.Direction.CW)
                path.moveTo(radius, 0f)
                path.lineTo(0f, 0f)
                path.lineTo(0f, radius)
                path.lineTo(radius, radius * 0.5f)
                path.close()
            }
        }

        override fun onDraw(canvas: Canvas) {
            canvas.drawPath(path, paint)
        }

        override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
            setMeasuredDimension(sizePx, sizePx)
        }
    }
}
