package r2u9.SimpleSSH.terminal

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.view.MotionEvent
import android.view.View
import android.widget.PopupWindow

/**
 * A draggable handle view for text selection in the terminal.
 */
class TextSelectionHandleView(
    private val terminalView: TerminalView,
    private val controller: TextSelectionCursorController,
    private val isLeft: Boolean
) {
    companion object {
        private const val HANDLE_SIZE_DP = 22
    }

    private val handleSizePx: Int
    private val popupWindow: PopupWindow
    private val handleView: HandleView

    private var isDragging = false
    private var touchDownX = 0f
    private var touchDownY = 0f
    private var positionX = 0
    private var positionY = 0
    private var dragStartX = 0
    private var dragStartY = 0

    init {
        val density = terminalView.context.resources.displayMetrics.density
        handleSizePx = (HANDLE_SIZE_DP * density).toInt()

        handleView = HandleView(terminalView.context, isLeft, handleSizePx)
        popupWindow = PopupWindow(handleView, handleSizePx, handleSizePx).apply {
            isClippingEnabled = false
            isSplitTouchEnabled = true
        }

        handleView.setOnTouchListener { _, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    touchDownX = event.rawX
                    touchDownY = event.rawY
                    dragStartX = positionX
                    dragStartY = positionY
                    isDragging = true
                }
                MotionEvent.ACTION_MOVE -> if (isDragging) {
                    controller.updatePosition(
                        this,
                        (dragStartX + event.rawX - touchDownX).toInt(),
                        (dragStartY + event.rawY - touchDownY).toInt()
                    )
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> isDragging = false
            }
            true
        }
    }

    fun getHandleHeight(): Int = handleSizePx
    fun isDragging(): Boolean = isDragging

    fun positionAtCursor(column: Int, row: Int, forceShow: Boolean) {
        val renderer = terminalView.renderer ?: return

        positionX = renderer.getPointX(column)
        positionY = renderer.getPointY(row + 1)

        val location = IntArray(2)
        terminalView.getLocationOnScreen(location)

        val handleX = location[0] + positionX - (if (isLeft) handleSizePx else 0)
        val handleY = location[1] + positionY

        if (forceShow || !popupWindow.isShowing) {
            popupWindow.showAtLocation(terminalView, 0, handleX, handleY)
        } else {
            popupWindow.update(handleX, handleY, -1, -1)
        }
    }

    fun hide() {
        isDragging = false
        if (popupWindow.isShowing) popupWindow.dismiss()
    }

    private class HandleView(context: Context, isLeft: Boolean, sizePx: Int) : View(context) {
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFF448AFF.toInt()
            style = Paint.Style.FILL
        }

        private val path = Path().apply {
            val r = sizePx / 2f
            addCircle(r, r * 1.5f, r, Path.Direction.CW)
            if (isLeft) {
                moveTo(r, 0f); lineTo(sizePx.toFloat(), 0f)
                lineTo(sizePx.toFloat(), r); lineTo(r, r * 0.5f); close()
            } else {
                moveTo(r, 0f); lineTo(0f, 0f)
                lineTo(0f, r); lineTo(r, r * 0.5f); close()
            }
        }

        override fun onDraw(canvas: Canvas) = canvas.drawPath(path, paint)
        override fun onMeasure(w: Int, h: Int) = setMeasuredDimension(sizePx, sizePx)
        private val sizePx = sizePx
    }
}
