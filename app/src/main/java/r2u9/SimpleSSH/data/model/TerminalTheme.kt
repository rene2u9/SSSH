package r2u9.SimpleSSH.data.model

import android.graphics.Color

data class TerminalTheme(
    val name: String,
    val backgroundColor: Int,
    val foregroundColor: Int,
    val cursorColor: Int,
    val black: Int,
    val red: Int,
    val green: Int,
    val yellow: Int,
    val blue: Int,
    val magenta: Int,
    val cyan: Int,
    val white: Int,
    val brightBlack: Int,
    val brightRed: Int,
    val brightGreen: Int,
    val brightYellow: Int,
    val brightBlue: Int,
    val brightMagenta: Int,
    val brightCyan: Int,
    val brightWhite: Int
) {
    companion object {
        val DEFAULT = TerminalTheme(
            name = "Default",
            backgroundColor = Color.parseColor("#1E1E1E"),
            foregroundColor = Color.parseColor("#D4D4D4"),
            cursorColor = Color.parseColor("#AEAFAD"),
            black = Color.parseColor("#000000"),
            red = Color.parseColor("#CD3131"),
            green = Color.parseColor("#0DBC79"),
            yellow = Color.parseColor("#E5E510"),
            blue = Color.parseColor("#2472C8"),
            magenta = Color.parseColor("#BC3FBC"),
            cyan = Color.parseColor("#11A8CD"),
            white = Color.parseColor("#E5E5E5"),
            brightBlack = Color.parseColor("#666666"),
            brightRed = Color.parseColor("#F14C4C"),
            brightGreen = Color.parseColor("#23D18B"),
            brightYellow = Color.parseColor("#F5F543"),
            brightBlue = Color.parseColor("#3B8EEA"),
            brightMagenta = Color.parseColor("#D670D6"),
            brightCyan = Color.parseColor("#29B8DB"),
            brightWhite = Color.parseColor("#FFFFFF")
        )

        val DRACULA = TerminalTheme(
            name = "Dracula",
            backgroundColor = Color.parseColor("#282A36"),
            foregroundColor = Color.parseColor("#F8F8F2"),
            cursorColor = Color.parseColor("#F8F8F2"),
            black = Color.parseColor("#21222C"),
            red = Color.parseColor("#FF5555"),
            green = Color.parseColor("#50FA7B"),
            yellow = Color.parseColor("#F1FA8C"),
            blue = Color.parseColor("#BD93F9"),
            magenta = Color.parseColor("#FF79C6"),
            cyan = Color.parseColor("#8BE9FD"),
            white = Color.parseColor("#F8F8F2"),
            brightBlack = Color.parseColor("#6272A4"),
            brightRed = Color.parseColor("#FF6E6E"),
            brightGreen = Color.parseColor("#69FF94"),
            brightYellow = Color.parseColor("#FFFFA5"),
            brightBlue = Color.parseColor("#D6ACFF"),
            brightMagenta = Color.parseColor("#FF92DF"),
            brightCyan = Color.parseColor("#A4FFFF"),
            brightWhite = Color.parseColor("#FFFFFF")
        )

        val MONOKAI = TerminalTheme(
            name = "Monokai",
            backgroundColor = Color.parseColor("#272822"),
            foregroundColor = Color.parseColor("#F8F8F2"),
            cursorColor = Color.parseColor("#F8F8F0"),
            black = Color.parseColor("#272822"),
            red = Color.parseColor("#F92672"),
            green = Color.parseColor("#A6E22E"),
            yellow = Color.parseColor("#F4BF75"),
            blue = Color.parseColor("#66D9EF"),
            magenta = Color.parseColor("#AE81FF"),
            cyan = Color.parseColor("#A1EFE4"),
            white = Color.parseColor("#F8F8F2"),
            brightBlack = Color.parseColor("#75715E"),
            brightRed = Color.parseColor("#F92672"),
            brightGreen = Color.parseColor("#A6E22E"),
            brightYellow = Color.parseColor("#F4BF75"),
            brightBlue = Color.parseColor("#66D9EF"),
            brightMagenta = Color.parseColor("#AE81FF"),
            brightCyan = Color.parseColor("#A1EFE4"),
            brightWhite = Color.parseColor("#F9F8F5")
        )

        val SOLARIZED_DARK = TerminalTheme(
            name = "Solarized Dark",
            backgroundColor = Color.parseColor("#002B36"),
            foregroundColor = Color.parseColor("#839496"),
            cursorColor = Color.parseColor("#93A1A1"),
            black = Color.parseColor("#073642"),
            red = Color.parseColor("#DC322F"),
            green = Color.parseColor("#859900"),
            yellow = Color.parseColor("#B58900"),
            blue = Color.parseColor("#268BD2"),
            magenta = Color.parseColor("#D33682"),
            cyan = Color.parseColor("#2AA198"),
            white = Color.parseColor("#EEE8D5"),
            brightBlack = Color.parseColor("#002B36"),
            brightRed = Color.parseColor("#CB4B16"),
            brightGreen = Color.parseColor("#586E75"),
            brightYellow = Color.parseColor("#657B83"),
            brightBlue = Color.parseColor("#839496"),
            brightMagenta = Color.parseColor("#6C71C4"),
            brightCyan = Color.parseColor("#93A1A1"),
            brightWhite = Color.parseColor("#FDF6E3")
        )

        val NORD = TerminalTheme(
            name = "Nord",
            backgroundColor = Color.parseColor("#2E3440"),
            foregroundColor = Color.parseColor("#D8DEE9"),
            cursorColor = Color.parseColor("#D8DEE9"),
            black = Color.parseColor("#3B4252"),
            red = Color.parseColor("#BF616A"),
            green = Color.parseColor("#A3BE8C"),
            yellow = Color.parseColor("#EBCB8B"),
            blue = Color.parseColor("#81A1C1"),
            magenta = Color.parseColor("#B48EAD"),
            cyan = Color.parseColor("#88C0D0"),
            white = Color.parseColor("#E5E9F0"),
            brightBlack = Color.parseColor("#4C566A"),
            brightRed = Color.parseColor("#BF616A"),
            brightGreen = Color.parseColor("#A3BE8C"),
            brightYellow = Color.parseColor("#EBCB8B"),
            brightBlue = Color.parseColor("#81A1C1"),
            brightMagenta = Color.parseColor("#B48EAD"),
            brightCyan = Color.parseColor("#8FBCBB"),
            brightWhite = Color.parseColor("#ECEFF4")
        )

        val GRUVBOX = TerminalTheme(
            name = "Gruvbox",
            backgroundColor = Color.parseColor("#282828"),
            foregroundColor = Color.parseColor("#EBDBB2"),
            cursorColor = Color.parseColor("#EBDBB2"),
            black = Color.parseColor("#282828"),
            red = Color.parseColor("#CC241D"),
            green = Color.parseColor("#98971A"),
            yellow = Color.parseColor("#D79921"),
            blue = Color.parseColor("#458588"),
            magenta = Color.parseColor("#B16286"),
            cyan = Color.parseColor("#689D6A"),
            white = Color.parseColor("#A89984"),
            brightBlack = Color.parseColor("#928374"),
            brightRed = Color.parseColor("#FB4934"),
            brightGreen = Color.parseColor("#B8BB26"),
            brightYellow = Color.parseColor("#FABD2F"),
            brightBlue = Color.parseColor("#83A598"),
            brightMagenta = Color.parseColor("#D3869B"),
            brightCyan = Color.parseColor("#8EC07C"),
            brightWhite = Color.parseColor("#EBDBB2")
        )

        val CLASSIC = TerminalTheme(
            name = "Classic",
            backgroundColor = Color.parseColor("#000000"),
            foregroundColor = Color.parseColor("#FFFFFF"),
            cursorColor = Color.parseColor("#FFFFFF"),
            black = Color.parseColor("#000000"),
            red = Color.parseColor("#AA0000"),
            green = Color.parseColor("#00AA00"),
            yellow = Color.parseColor("#AA5500"),
            blue = Color.parseColor("#0000AA"),
            magenta = Color.parseColor("#AA00AA"),
            cyan = Color.parseColor("#00AAAA"),
            white = Color.parseColor("#AAAAAA"),
            brightBlack = Color.parseColor("#555555"),
            brightRed = Color.parseColor("#FF5555"),
            brightGreen = Color.parseColor("#55FF55"),
            brightYellow = Color.parseColor("#FFFF55"),
            brightBlue = Color.parseColor("#5555FF"),
            brightMagenta = Color.parseColor("#FF55FF"),
            brightCyan = Color.parseColor("#55FFFF"),
            brightWhite = Color.parseColor("#FFFFFF")
        )

        val ALL_THEMES = listOf(DEFAULT, CLASSIC, DRACULA, MONOKAI, SOLARIZED_DARK, NORD, GRUVBOX)

        fun getByName(name: String): TerminalTheme {
            return ALL_THEMES.find { it.name.equals(name, ignoreCase = true) } ?: DEFAULT
        }
    }
}
