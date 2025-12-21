package r2u9.SimpleSSH.util

import android.content.Context
import android.content.SharedPreferences
import r2u9.SimpleSSH.data.model.TerminalTheme

class AppPreferences(context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    var defaultTheme: String
        get() = prefs.getString(KEY_DEFAULT_THEME, TerminalTheme.DEFAULT.name) ?: TerminalTheme.DEFAULT.name
        set(value) = prefs.edit().putString(KEY_DEFAULT_THEME, value).apply()

    var defaultFontSize: Int
        get() = prefs.getInt(KEY_DEFAULT_FONT_SIZE, 14)
        set(value) = prefs.edit().putInt(KEY_DEFAULT_FONT_SIZE, value).apply()

    var volumeUpAction: String
        get() = prefs.getString(KEY_VOLUME_UP_ACTION, VolumeKeyAction.UP_ARROW.name) ?: VolumeKeyAction.UP_ARROW.name
        set(value) = prefs.edit().putString(KEY_VOLUME_UP_ACTION, value).apply()

    var volumeDownAction: String
        get() = prefs.getString(KEY_VOLUME_DOWN_ACTION, VolumeKeyAction.DOWN_ARROW.name) ?: VolumeKeyAction.DOWN_ARROW.name
        set(value) = prefs.edit().putString(KEY_VOLUME_DOWN_ACTION, value).apply()

    var showExtraKeys: Boolean
        get() = prefs.getBoolean(KEY_SHOW_EXTRA_KEYS, true)
        set(value) = prefs.edit().putBoolean(KEY_SHOW_EXTRA_KEYS, value).apply()

    var scrollbackLines: Int
        get() = prefs.getInt(KEY_SCROLLBACK_LINES, 2000)
        set(value) = prefs.edit().putInt(KEY_SCROLLBACK_LINES, value).apply()

    var biometricEnabled: Boolean
        get() = prefs.getBoolean(KEY_BIOMETRIC_ENABLED, false)
        set(value) = prefs.edit().putBoolean(KEY_BIOMETRIC_ENABLED, value).apply()

    var accentColor: Int
        get() = prefs.getInt(KEY_ACCENT_COLOR, AccentColor.TEAL.colorValue)
        set(value) = prefs.edit().putInt(KEY_ACCENT_COLOR, value).apply()

    var accentColorName: String
        get() = prefs.getString(KEY_ACCENT_COLOR_NAME, AccentColor.TEAL.displayName) ?: AccentColor.TEAL.displayName
        set(value) = prefs.edit().putString(KEY_ACCENT_COLOR_NAME, value).apply()

    enum class AccentColor(val displayName: String, val colorValue: Int) {
        TEAL("Teal", 0xFF00BFA5.toInt()),
        BLUE("Blue", 0xFF2196F3.toInt()),
        PURPLE("Purple", 0xFF9C27B0.toInt()),
        PINK("Pink", 0xFFE91E63.toInt()),
        RED("Red", 0xFFF44336.toInt()),
        ORANGE("Orange", 0xFFFF9800.toInt()),
        YELLOW("Yellow", 0xFFFFEB3B.toInt()),
        GREEN("Green", 0xFF4CAF50.toInt()),
        CYAN("Cyan", 0xFF00BCD4.toInt()),
        INDIGO("Indigo", 0xFF3F51B5.toInt());

        companion object {
            fun fromName(name: String): AccentColor {
                return entries.find { it.displayName == name } ?: TEAL
            }

            fun fromColor(color: Int): AccentColor {
                return entries.find { it.colorValue == color } ?: TEAL
            }
        }
    }

    enum class VolumeKeyAction(val displayName: String) {
        NONE("None"),
        UP_ARROW("Up Arrow"),
        DOWN_ARROW("Down Arrow"),
        TAB("Tab"),
        ENTER("Enter"),
        CTRL("Ctrl"),
        ALT("Alt"),
        ESC("Escape");

        companion object {
            fun fromName(name: String): VolumeKeyAction {
                return entries.find { it.name == name } ?: UP_ARROW
            }
        }
    }

    companion object {
        private const val PREFS_NAME = "vibessh_preferences"
        private const val KEY_DEFAULT_THEME = "default_theme"
        private const val KEY_DEFAULT_FONT_SIZE = "default_font_size"
        private const val KEY_VOLUME_UP_ACTION = "volume_up_action"
        private const val KEY_VOLUME_DOWN_ACTION = "volume_down_action"
        private const val KEY_SHOW_EXTRA_KEYS = "show_extra_keys"
        private const val KEY_SCROLLBACK_LINES = "scrollback_lines"
        private const val KEY_BIOMETRIC_ENABLED = "biometric_enabled"
        private const val KEY_ACCENT_COLOR = "accent_color"
        private const val KEY_ACCENT_COLOR_NAME = "accent_color_name"

        @Volatile
        private var instance: AppPreferences? = null

        fun getInstance(context: Context): AppPreferences {
            return instance ?: synchronized(this) {
                instance ?: AppPreferences(context.applicationContext).also { instance = it }
            }
        }
    }
}
