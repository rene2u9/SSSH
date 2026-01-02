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

    private var startHandle: TextSelectionHandleView? = null
    private var endHandle: TextSelectionHandleView? = null
    private var actionMode: ActionMode? = null

    private var isSelectingText = false
    private var showStartTime = 0L
    private var selX1 = -1
    private var selY1 = -1
    private var selX2 = -1
    private var selY2 = -1

    private val handleHeight: Int get() = startHandle?.getHandleHeight() ?: 0

    fun show(event: MotionEvent) {
        if (startHandle == null) {
            startHandle = TextSelectionHandleView(terminalView, this, true)
            endHandle = TextSelectionHandleView(terminalView, this, false)
        }

        setInitialPosition(event)
        startHandle?.positionAtCursor(selX1, selY1, true)
        endHandle?.positionAtCursor(selX2 + 1, selY2, true)

        setupActionMode()
        showStartTime = System.currentTimeMillis()
        isSelectingText = true
    }

    fun hide(): Boolean {
        if (!isSelectingText) return false
        if (System.currentTimeMillis() - showStartTime < 300) return false

        startHandle?.hide()
        endHandle?.hide()
        actionMode?.finish()
        actionMode = null

        selX1 = -1; selY1 = -1; selX2 = -1; selY2 = -1
        isSelectingText = false
        return true
    }

    fun render() {
        if (!isSelectingText) return
        startHandle?.positionAtCursor(selX1, selY1, false)
        endHandle?.positionAtCursor(selX2 + 1, selY2, false)
        actionMode?.invalidate()
    }

    fun isActive(): Boolean = isSelectingText

    fun getSelectors(): IntArray = intArrayOf(selY1, selY2, selX1, selX2)

    fun getSelectedText(): String? {
        val emulator = terminalView.emulator ?: return null
        return emulator.getSelectedText(selX1, selY1, selX2, selY2)
    }

    fun updatePosition(handle: TextSelectionHandleView, x: Int, y: Int) {
        val renderer = terminalView.renderer ?: return
        val emulator = terminalView.emulator ?: return

        val col = (x / renderer.fontWidth).toInt().coerceIn(0, emulator.getColumns() - 1)
        val row = (y / renderer.fontLineSpacing).coerceIn(0, emulator.getRows() - 1)

        if (handle === startHandle) {
            if (row > selY2 || (row == selY2 && col > selX2)) {
                selX1 = selX2; selY1 = selY2
            } else {
                selX1 = col; selY1 = row
            }
        } else {
            if (row < selY1 || (row == selY1 && col < selX1)) {
                selX2 = selX1; selY2 = selY1
            } else {
                selX2 = col; selY2 = row
            }
        }
        terminalView.invalidate()
    }

    override fun onTouchModeChanged(isInTouchMode: Boolean) {
        if (!isInTouchMode) terminalView.stopTextSelectionMode()
    }

    fun onDetached() = hide()

    private fun setInitialPosition(event: MotionEvent) {
        val renderer = terminalView.renderer ?: return
        val emulator = terminalView.emulator ?: return

        val pos = renderer.getColumnAndRow(event.x, event.y)
        val col = pos[0].coerceIn(0, emulator.getColumns() - 1)
        val row = pos[1].coerceIn(0, emulator.getRows() - 1)

        selX1 = col; selY1 = row; selX2 = col; selY2 = row

        // Expand to word boundaries
        val screen = emulator.getVisibleScreen()
        if (row < screen.size && col < screen[row].size) {
            val c = screen[row][col].char
            if (c != ' ' && c.code >= 32) {
                var x = col
                while (x > 0 && screen[row][x - 1].char.let { it != ' ' && it.code >= 32 }) x--
                selX1 = x
                x = col
                while (x < emulator.getColumns() - 1 && screen[row][x + 1].char.let { it != ' ' && it.code >= 32 }) x++
                selX2 = x
            }
        }
    }

    private fun setupActionMode() {
        val callback = object : ActionMode.Callback {
            override fun onCreateActionMode(mode: ActionMode, menu: Menu): Boolean {
                val show = MenuItem.SHOW_AS_ACTION_IF_ROOM or MenuItem.SHOW_AS_ACTION_WITH_TEXT
                menu.add(Menu.NONE, 1, Menu.NONE, "Copy").setShowAsAction(show)
                menu.add(Menu.NONE, 2, Menu.NONE, "Paste").apply {
                    val clipboard = terminalView.context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    isEnabled = clipboard.hasPrimaryClip()
                    setShowAsAction(show)
                }
                return true
            }

            override fun onPrepareActionMode(mode: ActionMode, menu: Menu) = false

            override fun onActionItemClicked(mode: ActionMode, item: MenuItem): Boolean {
                if (!isSelectingText) return true
                val clipboard = terminalView.context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager

                when (item.itemId) {
                    1 -> { // Copy
                        getSelectedText()?.takeIf { it.isNotEmpty() }?.let {
                            clipboard.setPrimaryClip(ClipData.newPlainText("Terminal", it))
                        }
                        terminalView.stopTextSelectionMode()
                    }
                    2 -> { // Paste
                        terminalView.stopTextSelectionMode()
                        clipboard.primaryClip?.getItemAt(0)?.coerceToText(terminalView.context)?.let {
                            terminalView.onPaste(it.toString())
                        }
                    }
                }
                return true
            }

            override fun onDestroyActionMode(mode: ActionMode) { actionMode = null }
        }

        actionMode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            terminalView.startActionMode(object : ActionMode.Callback2() {
                override fun onCreateActionMode(mode: ActionMode, menu: Menu) = callback.onCreateActionMode(mode, menu)
                override fun onPrepareActionMode(mode: ActionMode, menu: Menu) = callback.onPrepareActionMode(mode, menu)
                override fun onActionItemClicked(mode: ActionMode, item: MenuItem) = callback.onActionItemClicked(mode, item)
                override fun onDestroyActionMode(mode: ActionMode) = callback.onDestroyActionMode(mode)

                override fun onGetContentRect(mode: ActionMode, view: View, outRect: Rect) {
                    val renderer = terminalView.renderer ?: return
                    outRect.set(
                        renderer.getPointX(minOf(selX1, selX2)),
                        (renderer.getPointY(minOf(selY1, selY2)) + handleHeight).coerceAtMost(terminalView.bottom),
                        renderer.getPointX(maxOf(selX1, selX2) + 1),
                        (renderer.getPointY(maxOf(selY1, selY2) + 1) + handleHeight).coerceAtMost(terminalView.bottom)
                    )
                }
            }, ActionMode.TYPE_FLOATING)
        } else {
            terminalView.startActionMode(callback)
        }
    }
}
