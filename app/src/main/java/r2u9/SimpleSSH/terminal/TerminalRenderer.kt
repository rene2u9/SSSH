package r2u9.SimpleSSH.terminal

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.Typeface
import androidx.core.graphics.ColorUtils
import r2u9.SimpleSSH.data.model.TerminalTheme
import kotlin.math.abs
import kotlin.math.ceil

/**
 * Renderer for terminal content using text run batching for performance.
 * Batches consecutive characters with the same style into "runs" and renders them together.
 */
class TerminalRenderer(textSize: Int, typeface: Typeface = Typeface.MONOSPACE) {

    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        this.typeface = typeface
        this.textSize = textSize.toFloat()
    }

    val fontWidth: Float = textPaint.measureText("X")
    val fontLineSpacing: Int = ceil(textPaint.fontSpacing.toDouble()).toInt()
    private val fontAscent: Int = ceil(textPaint.ascent().toDouble()).toInt()
    val fontLineSpacingAndAscent: Int = fontLineSpacing + fontAscent

    // Pre-computed ASCII widths for fast lookup
    private val asciiWidths = FloatArray(127) { i ->
        if (i > 0) textPaint.measureText(i.toChar().toString()) else 0f
    }

    // Reusable StringBuilder to avoid allocations during rendering
    private val runBuilder = StringBuilder(256)

    fun render(
        emulator: TerminalEmulator,
        canvas: Canvas,
        topRow: Int = 0,
        selX1: Int = -1,
        selY1: Int = -1,
        selX2: Int = -1,
        selY2: Int = -1,
        cursorBlink: Boolean = true
    ) {
        val theme = emulator.getTheme()
        val screen = emulator.getVisibleScreen()
        val rows = emulator.getRows()
        val columns = emulator.getColumns()
        val cursorCol = emulator.getCursorX()
        val cursorRow = emulator.getCursorY()
        val cursorVisible = emulator.isCursorVisible() && emulator.isAtBottom() && cursorBlink

        canvas.drawColor(theme.backgroundColor, PorterDuff.Mode.SRC)

        // Normalize selection once
        val (normSelY1, normSelX1, normSelY2, normSelX2) = normalizeSelection(selX1, selY1, selX2, selY2)

        for (row in 0 until rows) {
            // top of row, baseline is at top - fontAscent (fontAscent is negative)
            val rowTop = row * fontLineSpacing.toFloat()
            val baseline = rowTop - fontAscent
            val cursorX = if (row == cursorRow && cursorVisible) cursorCol else -1

            // Calculate selection for this row
            val (rowSelX1, rowSelX2) = if (normSelY1 != -1 && row in normSelY1..normSelY2) {
                val x1 = if (row == normSelY1) normSelX1 else 0
                val x2 = if (row == normSelY2) normSelX2 else columns - 1
                x1 to x2
            } else {
                -1 to -1
            }

            renderRow(canvas, screen[row], columns, rowTop, baseline, cursorX, rowSelX1, rowSelX2, theme)
        }
    }

    private fun normalizeSelection(x1: Int, y1: Int, x2: Int, y2: Int): IntArray {
        return if (y1 == -1) intArrayOf(-1, -1, -1, -1)
        else if (y1 > y2 || (y1 == y2 && x1 > x2)) intArrayOf(y2, x2, y1, x1)
        else intArrayOf(y1, x1, y2, x2)
    }

    private fun renderRow(
        canvas: Canvas,
        line: Array<TerminalChar>,
        columns: Int,
        rowTop: Float,
        baseline: Float,
        cursorX: Int,
        selX1: Int,
        selX2: Int,
        theme: TerminalTheme
    ) {
        var lastStyle = 0L
        var lastCursor = false
        var lastSelected = false
        var runStart = 0
        var runWidth = 0f

        runBuilder.setLength(0)

        for (col in 0 until columns) {
            val cell = line[col]
            val inCursor = col == cursorX
            val inSelection = col in selX1..selX2

            // Pack style into a long for fast comparison
            val style = packStyle(cell)
            val styleChanged = style != lastStyle || inCursor != lastCursor || inSelection != lastSelected

            if (styleChanged && col > 0) {
                drawRun(canvas, runBuilder, rowTop, baseline, runStart, col - runStart, runWidth,
                    lastCursor, lastSelected, unpackStyle(lastStyle), theme)
                runBuilder.setLength(0)
                runWidth = 0f
                runStart = col
            }

            // Append character
            val c = cell.char
            runBuilder.append(if (c.code >= 32 && !cell.hidden) c else ' ')
            runWidth += if (c.code < asciiWidths.size) asciiWidths[c.code] else textPaint.measureText(c.toString())

            lastStyle = style
            lastCursor = inCursor
            lastSelected = inSelection
        }

        // Draw final run
        if (runBuilder.isNotEmpty()) {
            drawRun(canvas, runBuilder, rowTop, baseline, runStart, columns - runStart, runWidth,
                lastCursor, lastSelected, unpackStyle(lastStyle), theme)
        }
    }

    // Pack cell style into a Long for fast comparison (avoids object allocations)
    private fun packStyle(cell: TerminalChar): Long {
        var flags = 0
        if (cell.bold) flags = flags or 1
        if (cell.italic) flags = flags or 2
        if (cell.underline) flags = flags or 4
        if (cell.strikethrough) flags = flags or 8
        if (cell.dim) flags = flags or 16
        return (cell.foreground.toLong() shl 32) or (cell.background.toLong() and 0xFFFFFFFFL) or (flags.toLong() shl 56)
    }

    private data class CellStyle(val fg: Int, val bg: Int, val bold: Boolean, val italic: Boolean,
                                  val underline: Boolean, val strikethrough: Boolean, val dim: Boolean)

    private fun unpackStyle(packed: Long): CellStyle {
        val flags = (packed shr 56).toInt()
        return CellStyle(
            fg = (packed shr 32).toInt(),
            bg = packed.toInt(),
            bold = (flags and 1) != 0,
            italic = (flags and 2) != 0,
            underline = (flags and 4) != 0,
            strikethrough = (flags and 8) != 0,
            dim = (flags and 16) != 0
        )
    }

    private fun drawRun(
        canvas: Canvas,
        text: CharSequence,
        rowTop: Float,
        baseline: Float,
        startCol: Int,
        colCount: Int,
        measuredWidth: Float,
        inCursor: Boolean,
        inSelection: Boolean,
        style: CellStyle,
        theme: TerminalTheme
    ) {
        var fg = style.fg
        var bg = style.bg

        if (inSelection) {
            val tmp = fg; fg = bg; bg = tmp
        }
        if (style.dim) {
            fg = ColorUtils.blendARGB(fg, theme.backgroundColor, 0.33f)
        }

        val left = startCol * fontWidth
        val right = left + colCount * fontWidth

        // Handle non-monospace characters by scaling
        val ratio = measuredWidth / fontWidth
        val needsScale = abs(ratio - colCount) > 0.01f
        var adjLeft = left
        var adjRight = right

        if (needsScale) {
            canvas.save()
            canvas.scale(colCount / ratio, 1f)
            adjLeft = left * ratio / colCount
            adjRight = right * ratio / colCount
        }

        val rowBottom = rowTop + fontLineSpacing

        // Draw background (selection highlight)
        if (bg != theme.backgroundColor) {
            textPaint.color = bg
            canvas.drawRect(adjLeft, rowTop, adjRight, rowBottom, textPaint)
        }

        // Draw cursor (underline style)
        if (inCursor) {
            textPaint.color = theme.cursorColor
            val cursorHeight = 4f
            canvas.drawRect(adjLeft, rowBottom - cursorHeight, adjRight, rowBottom, textPaint)
        }

        // Draw text
        textPaint.color = fg
        textPaint.isFakeBoldText = style.bold

        canvas.drawText(text, 0, text.length, adjLeft, baseline, textPaint)

        textPaint.isFakeBoldText = false

        if (needsScale) canvas.restore()
    }

    fun getColumnAndRow(x: Float, y: Float, topRow: Int = 0) = intArrayOf(
        (x / fontWidth).toInt(),
        ((y - fontLineSpacingAndAscent) / fontLineSpacing).toInt() + topRow
    )

    fun getPointX(column: Int): Int = (column * fontWidth).toInt()
    fun getPointY(row: Int, topRow: Int = 0): Int = (row - topRow) * fontLineSpacing
}
