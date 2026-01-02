package r2u9.SimpleSSH.terminal

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Rect
import android.os.Build
import android.view.ActionMode
import android.view.Menu
import android.view.MenuItem
import android.view.MotionEvent
import android.view.View
import android.view.ViewTreeObserver

/**
 * Controller for text selection in the terminal.
 * Manages selection handles, action mode, and clipboard operations.
 */
class TextSelectionCursorController(
    private val terminalView: TerminalView
) : ViewTreeObserver.OnTouchModeChangeListener {

    companion object {
        const val ACTION_COPY = 1
        const val ACTION_PASTE = 2
        const val ACTION_SELECT_ALL = 3
    }

    private var startHandle: TextSelectionHandleView? = null
    private var endHandle: TextSelectionHandleView? = null

    private var isSelectingText = false
    private var showStartTime = 0L

    private var selX1 = -1
    private var selY1 = -1
    private var selX2 = -1
    private var selY2 = -1

    private var actionMode: ActionMode? = null

    private val handleHeight: Int
        get() = startHandle?.getHandleHeight() ?: 0

    /**
     * Show the selection handles at the given event position.
     */
    fun show(event: MotionEvent) {
        if (startHandle == null) {
            startHandle = TextSelectionHandleView(terminalView, this, TextSelectionHandleView.LEFT)
            endHandle = TextSelectionHandleView(terminalView, this, TextSelectionHandleView.RIGHT)
        }

        setInitialTextSelectionPosition(event)

        startHandle?.positionAtCursor(selX1, selY1, true)
        endHandle?.positionAtCursor(selX2 + 1, selY2, true)

        setActionModeCallbacks()
        showStartTime = System.currentTimeMillis()
        isSelectingText = true
    }

    /**
     * Hide the selection handles and action mode.
     */
    fun hide(): Boolean {
        if (!isActive()) return false

        // Prevent hide calls right after show
        if (System.currentTimeMillis() - showStartTime < 300) {
            return false
        }

        startHandle?.hide()
        endHandle?.hide()

        actionMode?.finish()
        actionMode = null

        selX1 = -1
        selY1 = -1
        selX2 = -1
        selY2 = -1
        isSelectingText = false

        return true
    }

    /**
     * Render the selection handles at their current positions.
     */
    fun render() {
        if (!isActive()) return

        startHandle?.positionAtCursor(selX1, selY1, false)
        endHandle?.positionAtCursor(selX2 + 1, selY2, false)

        actionMode?.invalidate()
    }

    /**
     * Check if text selection is currently active.
     */
    fun isActive(): Boolean = isSelectingText

    /**
     * Get the current selection bounds.
     * @return IntArray of [startY, endY, startX, endX]
     */
    fun getSelectors(): IntArray {
        return intArrayOf(selY1, selY2, selX1, selX2)
    }

    /**
     * Get the currently selected text.
     */
    fun getSelectedText(): String? {
        val emulator = terminalView.emulator ?: return null
        return emulator.getSelectedText(selX1, selY1, selX2, selY2)
    }

    /**
     * Update handle position during drag.
     */
    fun updatePosition(handle: TextSelectionHandleView, x: Int, y: Int) {
        val renderer = terminalView.renderer ?: return
        val emulator = terminalView.emulator ?: return

        val column = (x / renderer.fontWidth).toInt().coerceIn(0, emulator.getColumns() - 1)
        val row = (y / renderer.fontLineSpacing).toInt().coerceIn(0, emulator.getRows() - 1)

        val isStartHandle = handle === startHandle

        if (isStartHandle) {
            // Constrain start handle to not go past end handle
            val newX = if (row > selY2 || (row == selY2 && column > selX2)) selX2 else column
            val newY = if (row > selY2) selY2 else row
            selX1 = newX
            selY1 = newY
        } else {
            // Constrain end handle to not go before start handle
            val newX = if (row < selY1 || (row == selY1 && column < selX1)) selX1 else column
            val newY = if (row < selY1) selY1 else row
            selX2 = newX
            selY2 = newY
        }

        terminalView.invalidate()
    }

    /**
     * Decrement Y positions when scrolling.
     */
    fun decrementYTextSelectionCursors(decrement: Int) {
        selY1 -= decrement
        selY2 -= decrement
    }

    /**
     * Check if start handle is being dragged.
     */
    fun isSelectionStartDragged(): Boolean = startHandle?.isDragging() == true

    /**
     * Check if end handle is being dragged.
     */
    fun isSelectionEndDragged(): Boolean = endHandle?.isDragging() == true

    /**
     * Get the action mode.
     */
    fun getActionMode(): ActionMode? = actionMode

    override fun onTouchModeChanged(isInTouchMode: Boolean) {
        if (!isInTouchMode) {
            terminalView.stopTextSelectionMode()
        }
    }

    fun onDetached() {
        hide()
    }

    private fun setInitialTextSelectionPosition(event: MotionEvent) {
        val renderer = terminalView.renderer ?: return
        val emulator = terminalView.emulator ?: return

        val pos = renderer.getColumnAndRow(event.x, event.y)
        val column = pos[0].coerceIn(0, emulator.getColumns() - 1)
        val row = pos[1].coerceIn(0, emulator.getRows() - 1)

        selX1 = column
        selY1 = row
        selX2 = column
        selY2 = row

        // Try to expand to word boundaries
        val screen = emulator.getVisibleScreen()
        if (row < screen.size && column < screen[row].size) {
            val char = screen[row][column].char
            if (char != ' ' && char.code >= 32) {
                // Expand left
                var x = column
                while (x > 0) {
                    val c = screen[row][x - 1].char
                    if (c == ' ' || c.code < 32) break
                    x--
                }
                selX1 = x

                // Expand right
                x = column
                while (x < emulator.getColumns() - 1) {
                    val c = screen[row][x + 1].char
                    if (c == ' ' || c.code < 32) break
                    x++
                }
                selX2 = x
            }
        }
    }

    private fun setActionModeCallbacks() {
        val callback = object : ActionMode.Callback {
            override fun onCreateActionMode(mode: ActionMode, menu: Menu): Boolean {
                val show = MenuItem.SHOW_AS_ACTION_IF_ROOM or MenuItem.SHOW_AS_ACTION_WITH_TEXT
                menu.add(Menu.NONE, ACTION_COPY, Menu.NONE, "Copy").setShowAsAction(show)
                menu.add(Menu.NONE, ACTION_PASTE, Menu.NONE, "Paste").apply {
                    val clipboard = terminalView.context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    isEnabled = clipboard.hasPrimaryClip()
                    setShowAsAction(show)
                }
                return true
            }

            override fun onPrepareActionMode(mode: ActionMode, menu: Menu): Boolean = false

            override fun onActionItemClicked(mode: ActionMode, item: MenuItem): Boolean {
                if (!isActive()) return true

                when (item.itemId) {
                    ACTION_COPY -> {
                        val selectedText = getSelectedText()
                        if (!selectedText.isNullOrEmpty()) {
                            val clipboard = terminalView.context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            val clip = ClipData.newPlainText("Terminal", selectedText)
                            clipboard.setPrimaryClip(clip)
                        }
                        terminalView.stopTextSelectionMode()
                    }
                    ACTION_PASTE -> {
                        terminalView.stopTextSelectionMode()
                        val clipboard = terminalView.context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        val clip = clipboard.primaryClip
                        if (clip != null && clip.itemCount > 0) {
                            val text = clip.getItemAt(0).coerceToText(terminalView.context)
                            terminalView.onPaste(text.toString())
                        }
                    }
                }
                return true
            }

            override fun onDestroyActionMode(mode: ActionMode) {
                actionMode = null
            }
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            actionMode = terminalView.startActionMode(object : ActionMode.Callback2() {
                override fun onCreateActionMode(mode: ActionMode, menu: Menu) =
                    callback.onCreateActionMode(mode, menu)

                override fun onPrepareActionMode(mode: ActionMode, menu: Menu) =
                    callback.onPrepareActionMode(mode, menu)

                override fun onActionItemClicked(mode: ActionMode, item: MenuItem) =
                    callback.onActionItemClicked(mode, item)

                override fun onDestroyActionMode(mode: ActionMode) =
                    callback.onDestroyActionMode(mode)

                override fun onGetContentRect(mode: ActionMode, view: View, outRect: Rect) {
                    val renderer = terminalView.renderer ?: return
                    val x1 = renderer.getPointX(selX1)
                    val x2 = renderer.getPointX(selX2 + 1)
                    val y1 = renderer.getPointY(selY1)
                    val y2 = renderer.getPointY(selY2 + 1)

                    val left = minOf(x1, x2)
                    val right = maxOf(x1, x2)
                    val top = (minOf(y1, y2) + handleHeight).coerceAtMost(terminalView.bottom)
                    val bottom = (maxOf(y1, y2) + handleHeight).coerceAtMost(terminalView.bottom)

                    outRect.set(left, top, right, bottom)
                }
            }, ActionMode.TYPE_FLOATING)
        } else {
            actionMode = terminalView.startActionMode(callback)
        }
    }
}
