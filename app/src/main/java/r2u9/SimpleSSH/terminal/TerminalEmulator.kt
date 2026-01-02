package r2u9.SimpleSSH.terminal

import r2u9.SimpleSSH.data.model.TerminalTheme
import java.nio.charset.Charset

data class TerminalChar(
    var char: Char = ' ',
    var foreground: Int = 0,
    var background: Int = 0,
    var bold: Boolean = false,
    var italic: Boolean = false,
    var underline: Boolean = false,
    var inverse: Boolean = false,
    var dim: Boolean = false,
    var blink: Boolean = false,
    var hidden: Boolean = false,
    var strikethrough: Boolean = false
)

class TerminalEmulator(
    private var columns: Int = 80,
    private var rows: Int = 24,
    private val maxScrollbackLines: Int = 2000
) {
    private var theme: TerminalTheme = TerminalTheme.DEFAULT
    private var mainScreen: Array<Array<TerminalChar>> = createScreen()
    private var altScreen: Array<Array<TerminalChar>> = createScreen()
    private var screen: Array<Array<TerminalChar>> = mainScreen
    private var useAltScreen = false

    private val scrollbackBuffer = ArrayDeque<Array<TerminalChar>>(maxScrollbackLines)
    private var scrollOffset = 0

    private var cursorX = 0
    private var cursorY = 0
    private var savedCursorX = 0
    private var savedCursorY = 0
    private var savedCursorXAlt = 0
    private var savedCursorYAlt = 0

    private var currentForeground: Int = 0
    private var currentBackground: Int = 0
    private var currentBold = false
    private var currentItalic = false
    private var currentUnderline = false
    private var currentInverse = false
    private var currentDim = false
    private var currentBlink = false
    private var currentHidden = false
    private var currentStrikethrough = false

    private var scrollTop = 0
    private var scrollBottom = rows - 1

    private val escapeBuffer = StringBuilder()
    private var parseState = ParseState.NORMAL

    private enum class ParseState {
        NORMAL,
        ESCAPE,
        CSI,
        CSI_PARAM,
        OSC,
        DCS,
        CHARSET
    }

    private var cursorVisible = true
    private var autoWrapMode = true
    private var originMode = false
    private var insertMode = false
    private var bracketedPasteMode = false
    private var applicationCursorKeys = false
    private var applicationKeypad = false
    private var lineFeedNewLineMode = false

    // Mouse tracking modes
    private var mouseTrackingMode = MouseTrackingMode.NONE
    private var mouseProtocol = MouseProtocol.X10

    enum class MouseTrackingMode {
        NONE,           // No mouse tracking
        BUTTON,         // Mode 1000: Report button press/release
        BUTTON_MOTION,  // Mode 1002: Report button press/release and motion while pressed
        ANY_MOTION      // Mode 1003: Report all motion events
    }

    enum class MouseProtocol {
        X10,            // Basic X10 protocol
        SGR             // Mode 1006: SGR extended coordinates
    }

    // Mouse button constants
    companion object {
        const val MOUSE_LEFT_BUTTON = 0
        const val MOUSE_MIDDLE_BUTTON = 1
        const val MOUSE_RIGHT_BUTTON = 2
        const val MOUSE_WHEELUP_BUTTON = 64
        const val MOUSE_WHEELDOWN_BUTTON = 65
        const val MOUSE_LEFT_BUTTON_MOVED = 32
    }

    private val utf8Buffer = ByteArray(4)
    private var utf8BytesRemaining = 0
    private var utf8ByteIndex = 0

    private val tabStops = mutableSetOf<Int>().apply {
        for (i in 0 until 320 step 8) add(i)
    }

    var onScreenUpdate: (() -> Unit)? = null
    var onSizeChanged: ((cols: Int, rows: Int) -> Unit)? = null

    init {
        resetColors()
    }

    private fun createScreen(): Array<Array<TerminalChar>> {
        return Array(rows) { Array(columns) { TerminalChar(foreground = theme.foregroundColor, background = theme.backgroundColor) } }
    }

    fun resize(newColumns: Int, newRows: Int) {
        if (newColumns == columns && newRows == rows) return

        mainScreen = resizeScreen(mainScreen, newColumns, newRows)
        altScreen = resizeScreen(altScreen, newColumns, newRows)
        screen = if (useAltScreen) altScreen else mainScreen

        columns = newColumns
        rows = newRows
        scrollBottom = rows - 1

        if (cursorX >= columns) cursorX = columns - 1
        if (cursorY >= rows) cursorY = rows - 1

        onSizeChanged?.invoke(columns, rows)
    }

    private fun resizeScreen(oldScreen: Array<Array<TerminalChar>>, newColumns: Int, newRows: Int): Array<Array<TerminalChar>> {
        val oldRows = oldScreen.size
        val oldCols = if (oldRows > 0) oldScreen[0].size else 0

        return Array(newRows) { y ->
            Array(newColumns) { x ->
                if (y < oldRows && x < oldCols) {
                    oldScreen[y][x].copy()
                } else {
                    TerminalChar(foreground = theme.foregroundColor, background = theme.backgroundColor)
                }
            }
        }
    }

    fun setTheme(newTheme: TerminalTheme) {
        val oldFg = theme.foregroundColor
        val oldBg = theme.backgroundColor
        theme = newTheme
        resetColors()

        for (scr in listOf(mainScreen, altScreen)) {
            for (row in scr) {
                for (cell in row) {
                    if (cell.foreground == oldFg) cell.foreground = theme.foregroundColor
                    if (cell.background == oldBg) cell.background = theme.backgroundColor
                }
            }
        }
        onScreenUpdate?.invoke()
    }

    private fun resetColors() {
        currentForeground = theme.foregroundColor
        currentBackground = theme.backgroundColor
    }

    fun processInput(data: ByteArray) {
        var i = 0
        while (i < data.size) {
            val byte = data[i].toInt() and 0xFF

            if (utf8BytesRemaining > 0) {
                if (byte and 0xC0 == 0x80) {
                    utf8Buffer[utf8ByteIndex++] = byte.toByte()
                    utf8BytesRemaining--
                    if (utf8BytesRemaining == 0) {
                        try {
                            val str = String(utf8Buffer, 0, utf8ByteIndex, Charset.forName("UTF-8"))
                            for (c in str) {
                                processChar(c)
                            }
                        } catch (e: Exception) {
                        }
                    }
                } else {
                    utf8BytesRemaining = 0
                    processChar(byte.toChar())
                }
            } else if (byte and 0x80 == 0) {
                processChar(byte.toChar())
            } else if (byte and 0xE0 == 0xC0) {
                utf8Buffer[0] = byte.toByte()
                utf8ByteIndex = 1
                utf8BytesRemaining = 1
            } else if (byte and 0xF0 == 0xE0) {
                utf8Buffer[0] = byte.toByte()
                utf8ByteIndex = 1
                utf8BytesRemaining = 2
            } else if (byte and 0xF8 == 0xF0) {
                utf8Buffer[0] = byte.toByte()
                utf8ByteIndex = 1
                utf8BytesRemaining = 3
            } else {
                processChar(byte.toChar())
            }
            i++
        }
        onScreenUpdate?.invoke()
    }

    fun processInput(data: String) {
        for (char in data) {
            processChar(char)
        }
        onScreenUpdate?.invoke()
    }

    private fun processChar(c: Char) {
        when (parseState) {
            ParseState.NORMAL -> processNormalChar(c)
            ParseState.ESCAPE -> processEscapeChar(c)
            ParseState.CSI, ParseState.CSI_PARAM -> processCsiChar(c)
            ParseState.OSC -> processOscChar(c)
            ParseState.DCS -> processDcsChar(c)
            ParseState.CHARSET -> {
                parseState = ParseState.NORMAL
            }
        }
    }

    private fun processNormalChar(c: Char) {
        when (c) {
            '\u001b' -> {
                parseState = ParseState.ESCAPE
                escapeBuffer.clear()
            }
            '\r' -> cursorX = 0
            '\n' -> {
                lineFeed()
                if (lineFeedNewLineMode) cursorX = 0
            }
            '\u000B', '\u000C' -> lineFeed()
            '\b' -> if (cursorX > 0) cursorX--
            '\t' -> tabForward()
            '\u0007' -> { }
            '\u000E' -> { }
            '\u000F' -> { }
            '\u0000' -> { }
            '\u007F' -> { }
            else -> {
                if (c.code >= 32) {
                    putChar(c)
                }
            }
        }
    }

    private fun processEscapeChar(c: Char) {
        when (c) {
            '[' -> {
                parseState = ParseState.CSI
                escapeBuffer.clear()
            }
            ']' -> {
                parseState = ParseState.OSC
                escapeBuffer.clear()
            }
            'P' -> {
                parseState = ParseState.DCS
                escapeBuffer.clear()
            }
            '(', ')', '*', '+', '-', '.', '/' -> {
                parseState = ParseState.CHARSET
            }
            '7' -> { saveCursor(); parseState = ParseState.NORMAL }
            '8' -> { restoreCursor(); parseState = ParseState.NORMAL }
            'D' -> { index(); parseState = ParseState.NORMAL }
            'M' -> { reverseIndex(); parseState = ParseState.NORMAL }
            'E' -> { nextLine(); parseState = ParseState.NORMAL }
            'H' -> { setTabStop(); parseState = ParseState.NORMAL }
            'c' -> { fullReset(); parseState = ParseState.NORMAL }
            '=' -> { applicationKeypad = true; parseState = ParseState.NORMAL }
            '>' -> { applicationKeypad = false; parseState = ParseState.NORMAL }
            'N', 'O' -> parseState = ParseState.NORMAL
            '\\' -> parseState = ParseState.NORMAL
            '#' -> parseState = ParseState.CHARSET
            ' ' -> parseState = ParseState.CHARSET
            '%' -> parseState = ParseState.CHARSET
            else -> {
                parseState = ParseState.NORMAL
            }
        }
    }

    private fun processCsiChar(c: Char) {
        when {
            c in '0'..'?' -> {
                escapeBuffer.append(c)
                parseState = ParseState.CSI_PARAM
            }
            c in ' '..'/' -> {
                escapeBuffer.append(c)
            }
            c in '@'..'~' -> {
                executeCsiSequence(escapeBuffer.toString(), c)
                parseState = ParseState.NORMAL
                escapeBuffer.clear()
            }
            else -> {
                parseState = ParseState.NORMAL
                escapeBuffer.clear()
            }
        }
    }

    private fun processOscChar(c: Char) {
        when {
            c == '\u0007' -> {
                processOscSequence(escapeBuffer.toString())
                parseState = ParseState.NORMAL
                escapeBuffer.clear()
            }
            c == '\u001b' -> {
                escapeBuffer.append(c)
            }
            escapeBuffer.endsWith("\u001b") && c == '\\' -> {
                processOscSequence(escapeBuffer.dropLast(1).toString())
                parseState = ParseState.NORMAL
                escapeBuffer.clear()
            }
            else -> {
                escapeBuffer.append(c)
                if (escapeBuffer.length > 4096) {
                    parseState = ParseState.NORMAL
                    escapeBuffer.clear()
                }
            }
        }
    }

    private fun processDcsChar(c: Char) {
        when {
            c == '\u001b' -> escapeBuffer.append(c)
            escapeBuffer.endsWith("\u001b") && c == '\\' -> {
                parseState = ParseState.NORMAL
                escapeBuffer.clear()
            }
            else -> {
                escapeBuffer.append(c)
                if (escapeBuffer.length > 4096) {
                    parseState = ParseState.NORMAL
                    escapeBuffer.clear()
                }
            }
        }
    }

    private fun putChar(c: Char) {
        if (cursorX >= columns) {
            if (autoWrapMode) {
                cursorX = 0
                lineFeed()
            } else {
                cursorX = columns - 1
            }
        }

        if (insertMode) {
            for (x in columns - 1 downTo cursorX + 1) {
                screen[cursorY][x] = screen[cursorY][x - 1].copy()
            }
        }

        screen[cursorY][cursorX] = TerminalChar(
            char = c,
            foreground = if (currentInverse) currentBackground else currentForeground,
            background = if (currentInverse) currentForeground else currentBackground,
            bold = currentBold,
            italic = currentItalic,
            underline = currentUnderline,
            inverse = currentInverse,
            dim = currentDim,
            blink = currentBlink,
            hidden = currentHidden,
            strikethrough = currentStrikethrough
        )
        cursorX++
    }

    private fun lineFeed() {
        if (cursorY == scrollBottom) {
            scrollUp()
        } else if (cursorY < rows - 1) {
            cursorY++
        }
    }

    private fun scrollUp(count: Int = 1) {
        val n = count.coerceAtMost(scrollBottom - scrollTop + 1)
        for (i in 0 until n) {
            if (!useAltScreen && scrollTop == 0) {
                val lineToSave = Array(columns) { x -> screen[0][x].copy() }
                scrollbackBuffer.addLast(lineToSave)
                while (scrollbackBuffer.size > maxScrollbackLines) {
                    scrollbackBuffer.removeFirst()
                }
            }

            for (y in scrollTop until scrollBottom) {
                screen[y] = screen[y + 1]
            }
            screen[scrollBottom] = Array(columns) { TerminalChar(foreground = theme.foregroundColor, background = theme.backgroundColor) }
        }

        if (scrollOffset > 0) {
            scrollOffset = 0
        }
    }

    private fun scrollDown(count: Int = 1) {
        val n = count.coerceAtMost(scrollBottom - scrollTop + 1)
        for (i in 0 until n) {
            for (y in scrollBottom downTo scrollTop + 1) {
                screen[y] = screen[y - 1]
            }
            screen[scrollTop] = Array(columns) { TerminalChar(foreground = theme.foregroundColor, background = theme.backgroundColor) }
        }
    }

    private fun index() {
        if (cursorY == scrollBottom) {
            scrollUp()
        } else if (cursorY < rows - 1) {
            cursorY++
        }
    }

    private fun reverseIndex() {
        if (cursorY == scrollTop) {
            scrollDown()
        } else if (cursorY > 0) {
            cursorY--
        }
    }

    private fun nextLine() {
        cursorX = 0
        lineFeed()
    }

    private fun tabForward() {
        val nextTab = tabStops.filter { it > cursorX }.minOrNull() ?: ((cursorX / 8 + 1) * 8)
        cursorX = minOf(nextTab, columns - 1)
    }

    private fun tabBackward() {
        val prevTab = tabStops.filter { it < cursorX }.maxOrNull() ?: 0
        cursorX = prevTab
    }

    private fun setTabStop() {
        tabStops.add(cursorX)
    }

    private fun clearTabStop(type: Int) {
        when (type) {
            0 -> tabStops.remove(cursorX)
            3 -> tabStops.clear()
        }
    }

    private fun processOscSequence(seq: String) {
    }

    private fun executeCsiSequence(paramStr: String, command: Char) {
        var params = paramStr
        val prefix = StringBuilder()

        while (params.isNotEmpty() && params[0] in "<=>?") {
            prefix.append(params[0])
            params = params.drop(1)
        }

        val intermediate = StringBuilder()
        while (params.isNotEmpty() && params.last() in ' '..'/') {
            intermediate.insert(0, params.last())
            params = params.dropLast(1)
        }

        val isPrivate = '?' in prefix.toString()
        val isDec = '>' in prefix.toString()

        val paramList = if (params.isEmpty()) {
            emptyList()
        } else {
            params.split(";").map { it.toIntOrNull() ?: 0 }
        }

        when (command) {
            'A' -> {
                val n = (paramList.getOrElse(0) { 1 }).coerceAtLeast(1)
                cursorY = maxOf(scrollTop, cursorY - n)
            }
            'B', 'e' -> {
                val n = (paramList.getOrElse(0) { 1 }).coerceAtLeast(1)
                cursorY = minOf(scrollBottom, cursorY + n)
            }
            'C', 'a' -> {
                val n = (paramList.getOrElse(0) { 1 }).coerceAtLeast(1)
                cursorX = minOf(columns - 1, cursorX + n)
            }
            'D' -> {
                val n = (paramList.getOrElse(0) { 1 }).coerceAtLeast(1)
                cursorX = maxOf(0, cursorX - n)
            }
            'E' -> {
                val n = (paramList.getOrElse(0) { 1 }).coerceAtLeast(1)
                cursorX = 0
                cursorY = minOf(scrollBottom, cursorY + n)
            }
            'F' -> {
                val n = (paramList.getOrElse(0) { 1 }).coerceAtLeast(1)
                cursorX = 0
                cursorY = maxOf(scrollTop, cursorY - n)
            }
            'G', '`' -> {
                val col = paramList.getOrElse(0) { 1 }
                cursorX = (col - 1).coerceIn(0, columns - 1)
            }
            'H', 'f' -> {
                val row = paramList.getOrElse(0) { 1 }
                val col = paramList.getOrElse(1) { 1 }
                cursorY = (row - 1).coerceIn(0, rows - 1)
                cursorX = (col - 1).coerceIn(0, columns - 1)
            }
            'I' -> {
                repeat((paramList.getOrElse(0) { 1 }).coerceAtLeast(1)) { tabForward() }
            }
            'J' -> {
                when (paramList.getOrElse(0) { 0 }) {
                    0 -> clearFromCursor()
                    1 -> clearToCursor()
                    2, 3 -> clearScreen()
                }
            }
            'K' -> {
                when (paramList.getOrElse(0) { 0 }) {
                    0 -> clearLineFromCursor()
                    1 -> clearLineToCursor()
                    2 -> clearLine()
                }
            }
            'L' -> {
                insertLines((paramList.getOrElse(0) { 1 }).coerceAtLeast(1))
            }
            'M' -> {
                deleteLines((paramList.getOrElse(0) { 1 }).coerceAtLeast(1))
            }
            'P' -> {
                deleteChars((paramList.getOrElse(0) { 1 }).coerceAtLeast(1))
            }
            'S' -> {
                scrollUp((paramList.getOrElse(0) { 1 }).coerceAtLeast(1))
            }
            'T' -> {
                scrollDown((paramList.getOrElse(0) { 1 }).coerceAtLeast(1))
            }
            'X' -> {
                eraseChars((paramList.getOrElse(0) { 1 }).coerceAtLeast(1))
            }
            'Z' -> {
                repeat((paramList.getOrElse(0) { 1 }).coerceAtLeast(1)) { tabBackward() }
            }
            '@' -> {
                insertChars((paramList.getOrElse(0) { 1 }).coerceAtLeast(1))
            }
            'b' -> {
                val count = (paramList.getOrElse(0) { 1 }).coerceAtLeast(1)
                val lastChar = if (cursorX > 0 && cursorY < rows) screen[cursorY][cursorX - 1].char else ' '
                repeat(count) { putChar(lastChar) }
            }
            'c' -> { }
            'd' -> {
                val row = paramList.getOrElse(0) { 1 }
                cursorY = (row - 1).coerceIn(0, rows - 1)
            }
            'g' -> {
                clearTabStop(paramList.getOrElse(0) { 0 })
            }
            'h' -> {
                if (isPrivate) {
                    setPrivateMode(paramList, true)
                } else {
                    setMode(paramList, true)
                }
            }
            'l' -> {
                if (isPrivate) {
                    setPrivateMode(paramList, false)
                } else {
                    setMode(paramList, false)
                }
            }
            'm' -> {
                processSgr(paramList)
            }
            'n' -> { }
            'p' -> {
                if (intermediate.toString() == "!") {
                    softReset()
                }
            }
            'q' -> { }
            'r' -> {
                val top = paramList.getOrElse(0) { 1 }
                val bottom = paramList.getOrElse(1) { rows }
                scrollTop = (top - 1).coerceIn(0, rows - 1)
                scrollBottom = (bottom - 1).coerceIn(scrollTop, rows - 1)
                cursorX = 0
                cursorY = if (originMode) scrollTop else 0
            }
            's' -> {
                if (!isPrivate) {
                    saveCursor()
                }
            }
            't' -> { }
            'u' -> {
                restoreCursor()
            }
        }
    }

    private fun setMode(params: List<Int>, enable: Boolean) {
        for (param in params) {
            when (param) {
                4 -> insertMode = enable
                20 -> lineFeedNewLineMode = enable
            }
        }
    }

    private fun setPrivateMode(params: List<Int>, enable: Boolean) {
        for (param in params) {
            when (param) {
                1 -> applicationCursorKeys = enable
                3 -> { }
                4 -> { }
                5 -> { }
                6 -> {
                    originMode = enable
                    cursorX = 0
                    cursorY = if (originMode) scrollTop else 0
                }
                7 -> autoWrapMode = enable
                12 -> { }
                25 -> cursorVisible = enable
                47 -> switchScreen(enable)
                1000 -> mouseTrackingMode = if (enable) MouseTrackingMode.BUTTON else MouseTrackingMode.NONE
                1002 -> mouseTrackingMode = if (enable) MouseTrackingMode.BUTTON_MOTION else MouseTrackingMode.NONE
                1003 -> mouseTrackingMode = if (enable) MouseTrackingMode.ANY_MOTION else MouseTrackingMode.NONE
                1006 -> mouseProtocol = if (enable) MouseProtocol.SGR else MouseProtocol.X10
                1015 -> { } // URXVT mouse mode - not implemented
                1004 -> { }
                1034 -> { }
                1047 -> switchScreen(enable)
                1048 -> if (enable) saveCursor() else restoreCursor()
                1049 -> {
                    if (enable) {
                        saveCursor()
                        switchScreen(true)
                        clearScreen()
                    } else {
                        switchScreen(false)
                        restoreCursor()
                    }
                }
                2004 -> bracketedPasteMode = enable
            }
        }
    }

    private fun switchScreen(toAlt: Boolean) {
        if (toAlt && !useAltScreen) {
            savedCursorXAlt = cursorX
            savedCursorYAlt = cursorY
            useAltScreen = true
            screen = altScreen
        } else if (!toAlt && useAltScreen) {
            useAltScreen = false
            screen = mainScreen
            cursorX = savedCursorXAlt
            cursorY = savedCursorYAlt
        }
    }

    private fun processSgr(params: List<Int>) {
        if (params.isEmpty()) {
            resetAttributes()
            return
        }

        var i = 0
        while (i < params.size) {
            when (val param = params[i]) {
                0 -> resetAttributes()
                1 -> currentBold = true
                2 -> currentDim = true
                3 -> currentItalic = true
                4 -> currentUnderline = true
                5, 6 -> currentBlink = true
                7 -> currentInverse = true
                8 -> currentHidden = true
                9 -> currentStrikethrough = true
                21 -> currentBold = false
                22 -> { currentBold = false; currentDim = false }
                23 -> currentItalic = false
                24 -> currentUnderline = false
                25 -> currentBlink = false
                27 -> currentInverse = false
                28 -> currentHidden = false
                29 -> currentStrikethrough = false
                30 -> currentForeground = theme.black
                31 -> currentForeground = theme.red
                32 -> currentForeground = theme.green
                33 -> currentForeground = theme.yellow
                34 -> currentForeground = theme.blue
                35 -> currentForeground = theme.magenta
                36 -> currentForeground = theme.cyan
                37 -> currentForeground = theme.white
                38 -> {
                    if (i + 2 < params.size && params[i + 1] == 5) {
                        currentForeground = get256Color(params[i + 2])
                        i += 2
                    } else if (i + 4 < params.size && params[i + 1] == 2) {
                        currentForeground = android.graphics.Color.rgb(
                            params[i + 2].coerceIn(0, 255),
                            params[i + 3].coerceIn(0, 255),
                            params[i + 4].coerceIn(0, 255)
                        )
                        i += 4
                    }
                }
                39 -> currentForeground = theme.foregroundColor
                40 -> currentBackground = theme.black
                41 -> currentBackground = theme.red
                42 -> currentBackground = theme.green
                43 -> currentBackground = theme.yellow
                44 -> currentBackground = theme.blue
                45 -> currentBackground = theme.magenta
                46 -> currentBackground = theme.cyan
                47 -> currentBackground = theme.white
                48 -> {
                    if (i + 2 < params.size && params[i + 1] == 5) {
                        currentBackground = get256Color(params[i + 2])
                        i += 2
                    } else if (i + 4 < params.size && params[i + 1] == 2) {
                        currentBackground = android.graphics.Color.rgb(
                            params[i + 2].coerceIn(0, 255),
                            params[i + 3].coerceIn(0, 255),
                            params[i + 4].coerceIn(0, 255)
                        )
                        i += 4
                    }
                }
                49 -> currentBackground = theme.backgroundColor
                90 -> currentForeground = theme.brightBlack
                91 -> currentForeground = theme.brightRed
                92 -> currentForeground = theme.brightGreen
                93 -> currentForeground = theme.brightYellow
                94 -> currentForeground = theme.brightBlue
                95 -> currentForeground = theme.brightMagenta
                96 -> currentForeground = theme.brightCyan
                97 -> currentForeground = theme.brightWhite
                100 -> currentBackground = theme.brightBlack
                101 -> currentBackground = theme.brightRed
                102 -> currentBackground = theme.brightGreen
                103 -> currentBackground = theme.brightYellow
                104 -> currentBackground = theme.brightBlue
                105 -> currentBackground = theme.brightMagenta
                106 -> currentBackground = theme.brightCyan
                107 -> currentBackground = theme.brightWhite
            }
            i++
        }
    }

    private fun get256Color(index: Int): Int {
        return when {
            index < 0 -> theme.foregroundColor
            index < 8 -> listOf(theme.black, theme.red, theme.green, theme.yellow, theme.blue, theme.magenta, theme.cyan, theme.white)[index]
            index < 16 -> listOf(theme.brightBlack, theme.brightRed, theme.brightGreen, theme.brightYellow, theme.brightBlue, theme.brightMagenta, theme.brightCyan, theme.brightWhite)[index - 8]
            index < 232 -> {
                val i = index - 16
                val r = if (i / 36 > 0) (i / 36) * 40 + 55 else 0
                val g = if ((i / 6) % 6 > 0) ((i / 6) % 6) * 40 + 55 else 0
                val b = if (i % 6 > 0) (i % 6) * 40 + 55 else 0
                android.graphics.Color.rgb(r, g, b)
            }
            index < 256 -> {
                val gray = (index - 232) * 10 + 8
                android.graphics.Color.rgb(gray, gray, gray)
            }
            else -> theme.foregroundColor
        }
    }

    private fun resetAttributes() {
        currentBold = false
        currentDim = false
        currentItalic = false
        currentUnderline = false
        currentBlink = false
        currentInverse = false
        currentHidden = false
        currentStrikethrough = false
        currentForeground = theme.foregroundColor
        currentBackground = theme.backgroundColor
    }

    private fun saveCursor() {
        savedCursorX = cursorX
        savedCursorY = cursorY
    }

    private fun restoreCursor() {
        cursorX = savedCursorX.coerceIn(0, columns - 1)
        cursorY = savedCursorY.coerceIn(0, rows - 1)
    }

    private fun clearScreen() {
        for (y in 0 until rows) {
            for (x in 0 until columns) {
                screen[y][x] = TerminalChar(foreground = theme.foregroundColor, background = theme.backgroundColor)
            }
        }
    }

    private fun clearFromCursor() {
        for (x in cursorX until columns) {
            screen[cursorY][x] = TerminalChar(foreground = theme.foregroundColor, background = theme.backgroundColor)
        }
        for (y in cursorY + 1 until rows) {
            for (x in 0 until columns) {
                screen[y][x] = TerminalChar(foreground = theme.foregroundColor, background = theme.backgroundColor)
            }
        }
    }

    private fun clearToCursor() {
        for (y in 0 until cursorY) {
            for (x in 0 until columns) {
                screen[y][x] = TerminalChar(foreground = theme.foregroundColor, background = theme.backgroundColor)
            }
        }
        for (x in 0..cursorX.coerceAtMost(columns - 1)) {
            screen[cursorY][x] = TerminalChar(foreground = theme.foregroundColor, background = theme.backgroundColor)
        }
    }

    private fun clearLine() {
        for (x in 0 until columns) {
            screen[cursorY][x] = TerminalChar(foreground = theme.foregroundColor, background = theme.backgroundColor)
        }
    }

    private fun clearLineFromCursor() {
        for (x in cursorX until columns) {
            screen[cursorY][x] = TerminalChar(foreground = theme.foregroundColor, background = theme.backgroundColor)
        }
    }

    private fun clearLineToCursor() {
        for (x in 0..cursorX.coerceAtMost(columns - 1)) {
            screen[cursorY][x] = TerminalChar(foreground = theme.foregroundColor, background = theme.backgroundColor)
        }
    }

    private fun insertLines(count: Int) {
        if (cursorY < scrollTop || cursorY > scrollBottom) return
        val n = count.coerceAtMost(scrollBottom - cursorY + 1)
        for (i in 0 until n) {
            for (y in scrollBottom downTo cursorY + 1) {
                screen[y] = screen[y - 1]
            }
            screen[cursorY] = Array(columns) { TerminalChar(foreground = theme.foregroundColor, background = theme.backgroundColor) }
        }
        cursorX = 0
    }

    private fun deleteLines(count: Int) {
        if (cursorY < scrollTop || cursorY > scrollBottom) return
        val n = count.coerceAtMost(scrollBottom - cursorY + 1)
        for (i in 0 until n) {
            for (y in cursorY until scrollBottom) {
                screen[y] = screen[y + 1]
            }
            screen[scrollBottom] = Array(columns) { TerminalChar(foreground = theme.foregroundColor, background = theme.backgroundColor) }
        }
        cursorX = 0
    }

    private fun insertChars(count: Int) {
        val n = count.coerceAtMost(columns - cursorX)
        for (x in columns - 1 downTo cursorX + n) {
            screen[cursorY][x] = screen[cursorY][x - n].copy()
        }
        for (x in cursorX until (cursorX + n).coerceAtMost(columns)) {
            screen[cursorY][x] = TerminalChar(foreground = theme.foregroundColor, background = theme.backgroundColor)
        }
    }

    private fun deleteChars(count: Int) {
        val n = count.coerceAtMost(columns - cursorX)
        for (x in cursorX until columns - n) {
            screen[cursorY][x] = screen[cursorY][x + n].copy()
        }
        for (x in (columns - n).coerceAtLeast(cursorX) until columns) {
            screen[cursorY][x] = TerminalChar(foreground = theme.foregroundColor, background = theme.backgroundColor)
        }
    }

    private fun eraseChars(count: Int) {
        for (x in cursorX until (cursorX + count).coerceAtMost(columns)) {
            screen[cursorY][x] = TerminalChar(foreground = theme.foregroundColor, background = theme.backgroundColor)
        }
    }

    private fun softReset() {
        cursorVisible = true
        originMode = false
        autoWrapMode = true
        insertMode = false
        applicationCursorKeys = false
        applicationKeypad = false
        lineFeedNewLineMode = false
        scrollTop = 0
        scrollBottom = rows - 1
        resetAttributes()
    }

    private fun fullReset() {
        softReset()
        cursorX = 0
        cursorY = 0
        savedCursorX = 0
        savedCursorY = 0
        switchScreen(false)
        clearScreen()
        tabStops.clear()
        for (i in 0 until 320 step 8) tabStops.add(i)
    }

    fun getScreen(): Array<Array<TerminalChar>> = screen
    fun getCursorX(): Int = cursorX
    fun getCursorY(): Int = cursorY
    fun getColumns(): Int = columns
    fun getRows(): Int = rows
    fun getTheme(): TerminalTheme = theme
    fun isCursorVisible(): Boolean = cursorVisible
    fun isApplicationCursorKeys(): Boolean = applicationCursorKeys
    fun isAlternateBufferActive(): Boolean = useAltScreen

    /** Check if mouse tracking is active */
    fun isMouseTrackingActive(): Boolean = mouseTrackingMode != MouseTrackingMode.NONE

    /** Check if any motion should be reported (mode 1003) */
    fun shouldReportAnyMotion(): Boolean = mouseTrackingMode == MouseTrackingMode.ANY_MOTION

    /** Check if button motion should be reported (mode 1002 or 1003) */
    fun shouldReportButtonMotion(): Boolean =
        mouseTrackingMode == MouseTrackingMode.BUTTON_MOTION || mouseTrackingMode == MouseTrackingMode.ANY_MOTION

    /** Callback for sending mouse events back to SSH */
    var onMouseEvent: ((String) -> Unit)? = null

    /**
     * Send a mouse event to the terminal.
     * @param button The button code (MOUSE_LEFT_BUTTON, etc.)
     * @param x Column (1-based)
     * @param y Row (1-based)
     * @param pressed True for press, false for release
     */
    fun sendMouseEvent(button: Int, x: Int, y: Int, pressed: Boolean) {
        if (mouseTrackingMode == MouseTrackingMode.NONE) return

        val adjustedX = x.coerceIn(1, columns)
        val adjustedY = y.coerceIn(1, rows)

        val response = when (mouseProtocol) {
            MouseProtocol.SGR -> {
                // SGR extended mouse protocol: ESC [ < Cb ; Cx ; Cy M/m
                val cb = button
                val suffix = if (pressed) 'M' else 'm'
                "\u001b[<$cb;$adjustedX;$adjustedY$suffix"
            }
            MouseProtocol.X10 -> {
                // X10 protocol: ESC [ M Cb Cx Cy
                // Button is encoded as: 32 + button for press, 35 for release
                val cb = if (pressed) 32 + button else 35
                val cx = 32 + adjustedX
                val cy = 32 + adjustedY
                // Only works for coordinates < 223
                if (adjustedX > 222 || adjustedY > 222) return
                "\u001b[M${cb.toChar()}${cx.toChar()}${cy.toChar()}"
            }
        }

        onMouseEvent?.invoke(response)
    }

    /**
     * Paste text, optionally using bracketed paste mode.
     */
    fun paste(text: String): String {
        return if (bracketedPasteMode) {
            "\u001b[200~$text\u001b[201~"
        } else {
            text
        }
    }

    fun getScrollOffset(): Int = scrollOffset
    fun getScrollbackSize(): Int = scrollbackBuffer.size
    fun getMaxScrollback(): Int = maxScrollbackLines

    fun scrollViewUp(lines: Int = 1): Boolean {
        val maxOffset = scrollbackBuffer.size
        val newOffset = (scrollOffset + lines).coerceAtMost(maxOffset)
        if (newOffset != scrollOffset) {
            scrollOffset = newOffset
            onScreenUpdate?.invoke()
            return true
        }
        return false
    }

    fun scrollViewDown(lines: Int = 1): Boolean {
        val newOffset = (scrollOffset - lines).coerceAtLeast(0)
        if (newOffset != scrollOffset) {
            scrollOffset = newOffset
            onScreenUpdate?.invoke()
            return true
        }
        return false
    }

    fun scrollToBottom() {
        if (scrollOffset != 0) {
            scrollOffset = 0
            onScreenUpdate?.invoke()
        }
    }

    fun scrollToTop() {
        val maxOffset = scrollbackBuffer.size
        if (scrollOffset != maxOffset) {
            scrollOffset = maxOffset
            onScreenUpdate?.invoke()
        }
    }

    fun isAtBottom(): Boolean = scrollOffset == 0

    fun getVisibleScreen(): Array<Array<TerminalChar>> {
        if (scrollOffset == 0 || useAltScreen) {
            return screen
        }

        val result = Array(rows) { y ->
            val scrollbackIndex = scrollbackBuffer.size - scrollOffset + y
            if (scrollbackIndex < 0) {
                Array(columns) { TerminalChar(foreground = theme.foregroundColor, background = theme.backgroundColor) }
            } else if (scrollbackIndex < scrollbackBuffer.size) {
                val scrollbackLine = scrollbackBuffer.elementAt(scrollbackIndex)
                if (scrollbackLine.size == columns) {
                    scrollbackLine
                } else {
                    Array(columns) { x ->
                        if (x < scrollbackLine.size) scrollbackLine[x].copy()
                        else TerminalChar(foreground = theme.foregroundColor, background = theme.backgroundColor)
                    }
                }
            } else {
                val screenY = scrollbackIndex - scrollbackBuffer.size
                if (screenY < rows) screen[screenY] else Array(columns) { TerminalChar(foreground = theme.foregroundColor, background = theme.backgroundColor) }
            }
        }
        return result
    }

    fun clearScrollback() {
        scrollbackBuffer.clear()
        scrollOffset = 0
        onScreenUpdate?.invoke()
    }

    fun getScreenText(): String {
        val sb = StringBuilder()
        for (y in 0 until rows) {
            for (x in 0 until columns) {
                sb.append(screen[y][x].char)
            }
            while (sb.isNotEmpty() && sb.last() == ' ') {
                sb.deleteCharAt(sb.length - 1)
            }
            if (y < rows - 1) {
                sb.append('\n')
            }
        }
        return sb.toString().trimEnd()
    }

    fun getSelectedText(startX: Int, startY: Int, endX: Int, endY: Int): String {
        val sb = StringBuilder()
        var sX = startX
        var sY = startY
        var eX = endX
        var eY = endY

        if (sY > eY || (sY == eY && sX > eX)) {
            val tmpX = sX
            val tmpY = sY
            sX = eX
            sY = eY
            eX = tmpX
            eY = tmpY
        }

        for (y in sY..eY) {
            val lineStart = if (y == sY) sX else 0
            val lineEnd = if (y == eY) eX else columns - 1
            for (x in lineStart..lineEnd.coerceAtMost(columns - 1)) {
                sb.append(screen[y][x].char)
            }
            while (sb.isNotEmpty() && sb.last() == ' ') {
                sb.deleteCharAt(sb.length - 1)
            }
            if (y < eY) {
                sb.append('\n')
            }
        }
        return sb.toString()
    }
}
