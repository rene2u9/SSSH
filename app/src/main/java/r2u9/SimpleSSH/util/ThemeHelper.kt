package r2u9.SimpleSSH.util

import android.content.res.ColorStateList
import android.graphics.Color
import androidx.core.graphics.ColorUtils

object ThemeHelper {

    fun getAccentColor(prefs: AppPreferences): Int {
        return prefs.accentColor
    }

    fun getOnAccentColor(accentColor: Int): Int {
        val luminance = ColorUtils.calculateLuminance(accentColor)
        return if (luminance > 0.5) Color.BLACK else Color.WHITE
    }

    fun getAccentContainerColor(accentColor: Int): Int {
        return ColorUtils.blendARGB(accentColor, Color.BLACK, 0.3f)
    }

    fun getOnAccentContainerColor(accentColor: Int): Int {
        return ColorUtils.blendARGB(accentColor, Color.WHITE, 0.6f)
    }

    fun createColorStateList(color: Int): ColorStateList {
        return ColorStateList.valueOf(color)
    }
}
