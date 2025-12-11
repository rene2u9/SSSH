package r2u9.vibe.util

import android.content.Context
import android.content.SharedPreferences
import r2u9.vibe.data.model.TerminalTheme

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

        @Volatile
        private var instance: AppPreferences? = null

        fun getInstance(context: Context): AppPreferences {
            return instance ?: synchronized(this) {
                instance ?: AppPreferences(context.applicationContext).also { instance = it }
            }
        }
    }
}
