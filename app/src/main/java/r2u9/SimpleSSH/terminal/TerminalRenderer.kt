package r2u9.SimpleSSH.terminal

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.Typeface
import androidx.core.graphics.ColorUtils
import r2u9.SimpleSSH.data.model.TerminalTheme

/**
 * Renderer for terminal content using text run batching for performance.
 *
 * Instead of rendering character by character, this renderer batches consecutive
 * characters with the same style into "runs" and renders them together.
 * This significantly improves rendering performance, especially for large terminals.
 */
class TerminalRenderer(
    val textSize: Int,
    val typeface: Typeface = Typeface.MONOSPACE
) {
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        this.typeface = this@TerminalRenderer.typeface
        this.textSize = this@TerminalRenderer.textSize.toFloat()
    }

    /** The width of a single monospace character */
    val fontWidth: Float = textPaint.measureText("X")

    /** The line spacing (height of a line) */
    val fontLineSpacing: Int = Math.ceil(textPaint.fontSpacing.toDouble()).toInt()

    /** The font ascent (distance from baseline to top) */
    private val fontAscent: Int = Math.ceil(textPaint.ascent().toDouble()).toInt()

    /** Combined line spacing and ascent for positioning */
    val fontLineSpacingAndAscent: Int = fontLineSpacing + fontAscent

    /** Pre-computed widths for ASCII characters */
    private val asciiMeasures = FloatArray(127) { i ->
        if (i > 0) textPaint.measureText(i.toChar().toString()) else 0f
    }

    /**
     * Cursor styles matching VT100/xterm standards.
     */
    enum class CursorStyle {
        BLOCK,
        UNDERLINE,
        BAR
    }

    /**
     * Render the terminal content to a canvas.
     *
     * @param emulator The terminal emulator containing the screen data
     * @param canvas The canvas to render to
     * @param topRow The top row to start rendering from (for scrollback)
     * @param selectionX1 Selection start column (-1 if no selection)
     * @param selectionY1 Selection start row
     * @param selectionX2 Selection end column
     * @param selectionY2 Selection end row
     * @param cursorStyle The style of cursor to render
     */
    fun render(
        emulator: TerminalEmulator,
        canvas: Canvas,
        topRow: Int = 0,
        selectionX1: Int = -1,
        selectionY1: Int = -1,
        selectionX2: Int = -1,
        selectionY2: Int = -1,
        cursorStyle: CursorStyle = CursorStyle.BLOCK
    ) {
        val theme = emulator.getTheme()
        val screen = emulator.getVisibleScreen()
        val rows = emulator.getRows()
        val columns = emulator.getColumns()
        val cursorCol = emulator.getCursorX()
        val cursorRow = emulator.getCursorY()
        val cursorVisible = emulator.isCursorVisible() && emulator.isAtBottom()

        // Draw background
        canvas.drawColor(theme.backgroundColor, PorterDuff.Mode.SRC)

        var heightOffset = fontLineSpacingAndAscent.toFloat()

        for (row in 0 until rows) {
            heightOffset += fontLineSpacing

            val cursorX = if (row == cursorRow && cursorVisible) cursorCol else -1

            // Determine selection bounds for this row
            var selX1 = -1
            var selX2 = -1
            if (selectionY1 != -1 && row >= minOf(selectionY1, selectionY2) && row <= maxOf(selectionY1, selectionY2)) {
                val (startY, startX, endY, endX) = normalizeSelection(selectionX1, selectionY1, selectionX2, selectionY2)
                if (row >= startY && row <= endY) {
                    selX1 = if (row == startY) startX else 0
                    selX2 = if (row == endY) endX else columns - 1
                }
            }

            val line = screen[row]

            // Render row using text runs
            renderRow(
                canvas, line, columns, heightOffset,
                cursorX, cursorStyle, selX1, selX2, theme
            )
        }
    }

    private fun normalizeSelection(x1: Int, y1: Int, x2: Int, y2: Int): IntArray {
        return if (y1 > y2 || (y1 == y2 && x1 > x2)) {
            intArrayOf(y2, x2, y1, x1)
        } else {
            intArrayOf(y1, x1, y2, x2)
        }
    }

    private fun renderRow(
        canvas: Canvas,
        line: Array<TerminalChar>,
        columns: Int,
        y: Float,
        cursorX: Int,
        cursorStyle: CursorStyle,
        selX1: Int,
        selX2: Int,
        theme: TerminalTheme
    ) {
        // Text run batching variables
        var lastFg = 0
        var lastBg = 0
        var lastBold = false
        var lastItalic = false
        var lastUnderline = false
        var lastStrikethrough = false
        var lastDim = false
        var lastInsideCursor = false
        var lastInsideSelection = false
        var runStartColumn = 0
        var runStartIndex = 0
        val runChars = StringBuilder()
        var measuredWidthForRun = 0f

        for (column in 0 until columns) {
            val cell = line[column]
            val char = cell.char
            val codePoint = char.code
            val codePointWcWidth = if (codePoint < 32) 1 else WcWidth.width(codePoint).coerceAtLeast(1)

            val insideCursor = cursorX == column || (codePointWcWidth == 2 && cursorX == column + 1)
            val insideSelection = column in selX1..selX2

            val fg = cell.foreground
            val bg = cell.background
            val bold = cell.bold
            val italic = cell.italic
            val underline = cell.underline
            val strikethrough = cell.strikethrough
            val dim = cell.dim

            // Check if we need to start a new run
            val styleChanged = fg != lastFg || bg != lastBg || bold != lastBold ||
                italic != lastItalic || underline != lastUnderline ||
                strikethrough != lastStrikethrough || dim != lastDim ||
                insideCursor != lastInsideCursor || insideSelection != lastInsideSelection

            if (styleChanged && column > 0) {
                // Draw the previous run
                drawTextRun(
                    canvas, runChars, y, runStartColumn,
                    column - runStartColumn, measuredWidthForRun,
                    if (lastInsideCursor) theme.cursorColor else 0,
                    cursorStyle, lastFg, lastBg, lastBold, lastItalic,
                    lastUnderline, lastStrikethrough, lastDim,
                    lastInsideCursor || lastInsideSelection, theme
                )

                // Start new run
                runChars.clear()
                measuredWidthForRun = 0f
                runStartColumn = column
                runStartIndex = column
            }

            // Add character to current run
            if (char.code >= 32 && !cell.hidden) {
                runChars.append(char)
            } else {
                runChars.append(' ')
            }

            // Measure character width
            val measuredWidth = if (codePoint < asciiMeasures.size) {
                asciiMeasures[codePoint]
            } else {
                textPaint.measureText(char.toString())
            }
            measuredWidthForRun += measuredWidth

            // Update last style
            lastFg = fg
            lastBg = bg
            lastBold = bold
            lastItalic = italic
            lastUnderline = underline
            lastStrikethrough = strikethrough
            lastDim = dim
            lastInsideCursor = insideCursor
            lastInsideSelection = insideSelection
        }

        // Draw final run
        if (runChars.isNotEmpty()) {
            drawTextRun(
                canvas, runChars, y, runStartColumn,
                columns - runStartColumn, measuredWidthForRun,
                if (lastInsideCursor) theme.cursorColor else 0,
                cursorStyle, lastFg, lastBg, lastBold, lastItalic,
                lastUnderline, lastStrikethrough, lastDim,
                lastInsideCursor || lastInsideSelection, theme
            )
        }
    }

    private fun drawTextRun(
        canvas: Canvas,
        text: CharSequence,
        y: Float,
        startColumn: Int,
        runWidthColumns: Int,
        measuredWidth: Float,
        cursorColor: Int,
        cursorStyle: CursorStyle,
        foreColor: Int,
        backColor: Int,
        bold: Boolean,
        italic: Boolean,
        underline: Boolean,
        strikethrough: Boolean,
        dim: Boolean,
        reverseVideo: Boolean,
        theme: TerminalTheme
    ) {
        var fg = foreColor
        var bg = backColor

        // Apply reverse video
        if (reverseVideo) {
            val tmp = fg
            fg = bg
            bg = tmp
        }

        // Apply dim effect
        if (dim) {
            fg = ColorUtils.blendARGB(fg, theme.backgroundColor, 0.33f)
        }

        val left = startColumn * fontWidth
        val right = left + runWidthColumns * fontWidth

        // Handle font width mismatch (for non-monospace characters)
        val mes = measuredWidth / fontWidth
        var savedMatrix = false
        var adjustedLeft = left
        var adjustedRight = right

        if (Math.abs(mes - runWidthColumns) > 0.01) {
            canvas.save()
            canvas.scale(runWidthColumns / mes, 1f)
            adjustedLeft = left * mes / runWidthColumns
            adjustedRight = right * mes / runWidthColumns
            savedMatrix = true
        }

        // Draw background if not default
        if (bg != theme.backgroundColor) {
            textPaint.color = bg
            canvas.drawRect(
                adjustedLeft, y - fontLineSpacingAndAscent + fontAscent,
                adjustedRight, y, textPaint
            )
        }

        // Draw cursor
        if (cursorColor != 0) {
            textPaint.color = cursorColor
            val cursorHeight = fontLineSpacingAndAscent - fontAscent
            val cursorTop: Float
            val cursorRight: Float

            when (cursorStyle) {
                CursorStyle.BLOCK -> {
                    cursorTop = y - cursorHeight
                    cursorRight = adjustedRight
                }
                CursorStyle.UNDERLINE -> {
                    cursorTop = y - cursorHeight / 4
                    cursorRight = adjustedRight
                }
                CursorStyle.BAR -> {
                    cursorTop = y - cursorHeight
                    cursorRight = adjustedLeft + fontWidth / 4
                }
            }
            canvas.drawRect(adjustedLeft, cursorTop, cursorRight, y, textPaint)
        }

        // Draw text
        textPaint.isFakeBoldText = bold
        textPaint.isUnderlineText = underline
        textPaint.textSkewX = if (italic) -0.35f else 0f
        textPaint.isStrikeThruText = strikethrough
        textPaint.color = fg

        canvas.drawText(
            text, 0, text.length,
            adjustedLeft, y - fontLineSpacingAndAscent, textPaint
        )

        if (savedMatrix) {
            canvas.restore()
        }
    }

    /**
     * Get column and row from screen coordinates.
     */
    fun getColumnAndRow(x: Float, y: Float, topRow: Int = 0): IntArray {
        val column = (x / fontWidth).toInt()
        val row = ((y - fontLineSpacingAndAscent) / fontLineSpacing).toInt() + topRow
        return intArrayOf(column, row)
    }

    /**
     * Get screen X coordinate for a column.
     */
    fun getPointX(column: Int): Int {
        return Math.round(column * fontWidth)
    }

    /**
     * Get screen Y coordinate for a row.
     */
    fun getPointY(row: Int, topRow: Int = 0): Int {
        return ((row - topRow) * fontLineSpacing)
    }
}
