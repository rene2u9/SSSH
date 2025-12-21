package r2u9.SimpleSSH.ui

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.color.MaterialColors
import r2u9.SimpleSSH.util.AppPreferences

abstract class BaseActivity : AppCompatActivity() {

    protected lateinit var prefs: AppPreferences

    override fun onCreate(savedInstanceState: Bundle?) {
        prefs = AppPreferences.getInstance(this)
        applyAccentColor()
        super.onCreate(savedInstanceState)
    }

    private fun applyAccentColor() {
        val accentColor = prefs.accentColor
        theme.applyStyle(getAccentColorStyle(accentColor), true)
    }

    private fun getAccentColorStyle(color: Int): Int {
        return when (AppPreferences.AccentColor.fromColor(color)) {
            AppPreferences.AccentColor.TEAL -> r2u9.SimpleSSH.R.style.AccentTeal
            AppPreferences.AccentColor.BLUE -> r2u9.SimpleSSH.R.style.AccentBlue
            AppPreferences.AccentColor.PURPLE -> r2u9.SimpleSSH.R.style.AccentPurple
            AppPreferences.AccentColor.PINK -> r2u9.SimpleSSH.R.style.AccentPink
            AppPreferences.AccentColor.RED -> r2u9.SimpleSSH.R.style.AccentRed
            AppPreferences.AccentColor.ORANGE -> r2u9.SimpleSSH.R.style.AccentOrange
            AppPreferences.AccentColor.YELLOW -> r2u9.SimpleSSH.R.style.AccentYellow
            AppPreferences.AccentColor.GREEN -> r2u9.SimpleSSH.R.style.AccentGreen
            AppPreferences.AccentColor.CYAN -> r2u9.SimpleSSH.R.style.AccentCyan
            AppPreferences.AccentColor.INDIGO -> r2u9.SimpleSSH.R.style.AccentIndigo
        }
    }
}
