package r2u9.SimpleSSH.ui

import android.content.Intent
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.launch
import r2u9.SimpleSSH.R
import r2u9.SimpleSSH.data.AppDatabase
import r2u9.SimpleSSH.data.model.TerminalTheme
import r2u9.SimpleSSH.data.repository.SshConnectionRepository
import r2u9.SimpleSSH.databinding.ActivitySettingsBinding
import r2u9.SimpleSSH.util.AppPreferences
import r2u9.SimpleSSH.util.BiometricHelper
import r2u9.SimpleSSH.util.ConfigExporter

class SettingsActivity : BaseActivity() {

    private lateinit var binding: ActivitySettingsBinding
    private lateinit var repository: SshConnectionRepository

    private val importLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let { importFromFile(it) }
    }

    private val exportLauncher = registerForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        uri?.let { exportToFile(it) }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        repository = SshConnectionRepository(AppDatabase.getDatabase(this).sshConnectionDao())

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
        binding.accentColorRow.setOnClickListener {
            showAccentColorDialog()
        }

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

        binding.importRow.setOnClickListener {
            importLauncher.launch(arrayOf("application/json", "*/*"))
        }

        binding.exportRow.setOnClickListener {
            BiometricHelper.authenticateIfEnabled(
                activity = this,
                prefs = prefs,
                title = "Authenticate",
                subtitle = "Verify your identity to export connections",
                onSuccess = {
                    exportLauncher.launch("simplessh_connections.json")
                },
                onError = { error ->
                    showError("Authentication failed: $error")
                }
            )
        }

        setupBiometricSwitch()
    }

    private fun setupBiometricSwitch() {
        val canUseBiometric = BiometricHelper.canAuthenticate(this)
        binding.biometricSwitch.isEnabled = canUseBiometric
        binding.biometricSwitch.isChecked = prefs.biometricEnabled && canUseBiometric

        if (!canUseBiometric) {
            binding.biometricDescription.text = "Biometric authentication not available"
        }

        binding.biometricSwitch.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                BiometricHelper.authenticate(
                    activity = this,
                    title = "Enable Biometric",
                    subtitle = "Verify your identity to enable biometric protection",
                    onSuccess = {
                        prefs.biometricEnabled = true
                        Toast.makeText(this, "Biometric protection enabled", Toast.LENGTH_SHORT).show()
                    },
                    onError = { error ->
                        binding.biometricSwitch.isChecked = false
                        Toast.makeText(this, "Failed: $error", Toast.LENGTH_SHORT).show()
                    },
                    onFailed = {
                    }
                )
            } else {
                prefs.biometricEnabled = false
            }
        }

        binding.biometricRow.setOnClickListener {
            if (canUseBiometric) {
                binding.biometricSwitch.toggle()
            }
        }
    }

    private fun updateDisplayedValues() {
        binding.accentColorValue.text = prefs.accentColorName
        updateAccentColorPreview()
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

    private fun updateAccentColorPreview() {
        val drawable = GradientDrawable()
        drawable.shape = GradientDrawable.OVAL
        drawable.setColor(prefs.accentColor)
        binding.accentColorPreview.background = drawable
    }

    private fun importFromFile(uri: Uri) {
        try {
            val content = ConfigExporter.readFromUri(this, uri)
            val connections = ConfigExporter.importConnections(content)
            lifecycleScope.launch {
                connections.forEach { connection ->
                    repository.insertConnection(connection.copy(id = 0))
                }
                Snackbar.make(binding.root, "Imported ${connections.size} connections", Snackbar.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            showError("Import failed: ${e.message}")
        }
    }

    private fun exportToFile(uri: Uri) {
        lifecycleScope.launch {
            try {
                val connections = repository.getAllConnectionsList()
                val content = ConfigExporter.exportConnections(connections)
                ConfigExporter.writeToUri(this@SettingsActivity, uri, content)
                Snackbar.make(binding.root, "Exported ${connections.size} connections", Snackbar.LENGTH_SHORT).show()
            } catch (e: Exception) {
                showError("Export failed: ${e.message}")
            }
        }
    }

    private fun showError(message: String) {
        Snackbar.make(binding.root, message, Snackbar.LENGTH_LONG).show()
    }

    private fun showAccentColorDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_accent_color, null)
        val recyclerView = dialogView.findViewById<RecyclerView>(R.id.colorGrid)

        val colors = AppPreferences.AccentColor.entries.toList()
        val currentColor = AppPreferences.AccentColor.fromColor(prefs.accentColor)

        var dialog: androidx.appcompat.app.AlertDialog? = null

        val adapter = ColorAdapter(colors, currentColor) { selectedColor ->
            prefs.accentColor = selectedColor.colorValue
            prefs.accentColorName = selectedColor.displayName
            updateDisplayedValues()
            dialog?.dismiss()
            restartActivity()
        }

        recyclerView.layoutManager = GridLayoutManager(this, 5)
        recyclerView.adapter = adapter

        dialog = MaterialAlertDialogBuilder(this)
            .setTitle("Accent Color")
            .setView(dialogView)
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun restartActivity() {
        val intent = Intent(this, SettingsActivity::class.java)
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK)
        finish()
        startActivity(intent)
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

    private inner class ColorAdapter(
        private val colors: List<AppPreferences.AccentColor>,
        private var selectedColor: AppPreferences.AccentColor,
        private val onColorSelected: (AppPreferences.AccentColor) -> Unit
    ) : RecyclerView.Adapter<ColorAdapter.ColorViewHolder>() {

        inner class ColorViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
            val colorCircle: View = itemView.findViewById(R.id.colorCircle)
            val checkIcon: ImageView = itemView.findViewById(R.id.checkIcon)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ColorViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_color_option, parent, false)
            return ColorViewHolder(view)
        }

        override fun onBindViewHolder(holder: ColorViewHolder, position: Int) {
            val color = colors[position]
            val drawable = GradientDrawable()
            drawable.shape = GradientDrawable.OVAL
            drawable.setColor(color.colorValue)
            holder.colorCircle.background = drawable
            holder.checkIcon.visibility = if (color == selectedColor) View.VISIBLE else View.GONE

            holder.itemView.setOnClickListener {
                onColorSelected(color)
            }
        }

        override fun getItemCount(): Int = colors.size
    }
}
