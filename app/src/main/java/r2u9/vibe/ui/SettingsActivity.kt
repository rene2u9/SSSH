package r2u9.vibe.ui

import android.os.Bundle
import android.widget.SeekBar
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import r2u9.vibe.R
import r2u9.vibe.data.model.TerminalTheme
import r2u9.vibe.databinding.ActivitySettingsBinding
import r2u9.vibe.util.AppPreferences

class SettingsActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySettingsBinding
    private lateinit var prefs: AppPreferences

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        prefs = AppPreferences.getInstance(this)

        setupInsets()
        setupToolbar()
        setupClickListeners()
        updateDisplayedValues()
    }

    private fun setupInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(binding.main) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }
    }

    private fun setupToolbar() {
        binding.toolbar.setNavigationOnClickListener {
            finish()
        }
    }

    private fun setupClickListeners() {
        binding.themeRow.setOnClickListener {
            showThemeDialog()
        }

        binding.fontSizeRow.setOnClickListener {
            showFontSizeDialog()
        }

        binding.volumeUpRow.setOnClickListener {
            showVolumeKeyDialog(isVolumeUp = true)
        }

        binding.volumeDownRow.setOnClickListener {
            showVolumeKeyDialog(isVolumeUp = false)
        }

        binding.extraKeysSwitch.isChecked = prefs.showExtraKeys
        binding.extraKeysSwitch.setOnCheckedChangeListener { _, isChecked ->
            prefs.showExtraKeys = isChecked
        }
        binding.extraKeysRow.setOnClickListener {
            binding.extraKeysSwitch.toggle()
        }

        binding.scrollbackRow.setOnClickListener {
            showScrollbackDialog()
        }
    }

    private fun updateDisplayedValues() {
        binding.themeValue.text = prefs.defaultTheme
        binding.fontSizeValue.text = "${prefs.defaultFontSize} sp"
        binding.scrollbackValue.text = "${prefs.scrollbackLines} lines"
        binding.volumeUpValue.text = AppPreferences.VolumeKeyAction.fromName(prefs.volumeUpAction).displayName
        binding.volumeDownValue.text = AppPreferences.VolumeKeyAction.fromName(prefs.volumeDownAction).displayName
        try {
            val packageInfo = packageManager.getPackageInfo(packageName, 0)
            binding.versionText.text = "Version ${packageInfo.versionName}"
        } catch (e: Exception) {
            binding.versionText.text = "Version 1.0"
        }
    }

    private fun showThemeDialog() {
        val themes = TerminalTheme.ALL_THEMES.map { it.name }.toTypedArray()
        val currentIndex = themes.indexOfFirst { it.equals(prefs.defaultTheme, ignoreCase = true) }

        MaterialAlertDialogBuilder(this)
            .setTitle("Default Terminal Theme")
            .setSingleChoiceItems(themes, currentIndex) { dialog, which ->
                prefs.defaultTheme = themes[which]
                updateDisplayedValues()
                dialog.dismiss()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showFontSizeDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_font_size, null)
        val seekBar = dialogView.findViewById<SeekBar>(R.id.fontSizeSeekBar)
        val sizeLabel = dialogView.findViewById<TextView>(R.id.fontSizeLabel)

        seekBar.min = 8
        seekBar.max = 32
        seekBar.progress = prefs.defaultFontSize
        sizeLabel.text = "${prefs.defaultFontSize} sp"

        seekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                sizeLabel.text = "$progress sp"
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        MaterialAlertDialogBuilder(this)
            .setTitle("Default Font Size")
            .setView(dialogView)
            .setPositiveButton("OK") { _, _ ->
                prefs.defaultFontSize = seekBar.progress
                updateDisplayedValues()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showVolumeKeyDialog(isVolumeUp: Boolean) {
        val actions = AppPreferences.VolumeKeyAction.entries.toTypedArray()
        val actionNames = actions.map { it.displayName }.toTypedArray()
        val currentAction = if (isVolumeUp) {
            AppPreferences.VolumeKeyAction.fromName(prefs.volumeUpAction)
        } else {
            AppPreferences.VolumeKeyAction.fromName(prefs.volumeDownAction)
        }
        val currentIndex = actions.indexOf(currentAction)

        MaterialAlertDialogBuilder(this)
            .setTitle(if (isVolumeUp) "Volume Up Action" else "Volume Down Action")
            .setSingleChoiceItems(actionNames, currentIndex) { dialog, which ->
                val selectedAction = actions[which]
                if (isVolumeUp) {
                    prefs.volumeUpAction = selectedAction.name
                } else {
                    prefs.volumeDownAction = selectedAction.name
                }
                updateDisplayedValues()
                dialog.dismiss()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showScrollbackDialog() {
        val options = arrayOf("500", "1000", "2000", "5000", "10000")
        val currentValue = prefs.scrollbackLines
        val currentIndex = options.indexOfFirst { it.toInt() == currentValue }.takeIf { it >= 0 } ?: 2

        MaterialAlertDialogBuilder(this)
            .setTitle("Scrollback Lines")
            .setSingleChoiceItems(options, currentIndex) { dialog, which ->
                prefs.scrollbackLines = options[which].toInt()
                updateDisplayedValues()
                dialog.dismiss()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
}
