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
            AppPreferences.AccentColor.LIGHT_BLUE -> r2u9.SimpleSSH.R.style.AccentLightBlue
            AppPreferences.AccentColor.CYAN -> r2u9.SimpleSSH.R.style.AccentCyan
            AppPreferences.AccentColor.GREEN -> r2u9.SimpleSSH.R.style.AccentGreen
            AppPreferences.AccentColor.LIGHT_GREEN -> r2u9.SimpleSSH.R.style.AccentLightGreen
            AppPreferences.AccentColor.LIME -> r2u9.SimpleSSH.R.style.AccentLime
            AppPreferences.AccentColor.YELLOW -> r2u9.SimpleSSH.R.style.AccentYellow
            AppPreferences.AccentColor.AMBER -> r2u9.SimpleSSH.R.style.AccentAmber
            AppPreferences.AccentColor.ORANGE -> r2u9.SimpleSSH.R.style.AccentOrange
            AppPreferences.AccentColor.DEEP_ORANGE -> r2u9.SimpleSSH.R.style.AccentDeepOrange
            AppPreferences.AccentColor.RED -> r2u9.SimpleSSH.R.style.AccentRed
            AppPreferences.AccentColor.PINK -> r2u9.SimpleSSH.R.style.AccentPink
            AppPreferences.AccentColor.PURPLE -> r2u9.SimpleSSH.R.style.AccentPurple
            AppPreferences.AccentColor.DEEP_PURPLE -> r2u9.SimpleSSH.R.style.AccentDeepPurple
            AppPreferences.AccentColor.INDIGO -> r2u9.SimpleSSH.R.style.AccentIndigo
            AppPreferences.AccentColor.BROWN -> r2u9.SimpleSSH.R.style.AccentBrown
            AppPreferences.AccentColor.GREY -> r2u9.SimpleSSH.R.style.AccentGrey
            AppPreferences.AccentColor.BLUE_GREY -> r2u9.SimpleSSH.R.style.AccentBlueGrey
            AppPreferences.AccentColor.WHITE -> r2u9.SimpleSSH.R.style.AccentWhite
        }
    }
}
