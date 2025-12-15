package r2u9.SimpleSSH.ui

import android.app.Dialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.os.IBinder
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import r2u9.SimpleSSH.R
import r2u9.SimpleSSH.data.model.AuthType
import r2u9.SimpleSSH.data.model.TerminalTheme
import r2u9.SimpleSSH.databinding.ActivityTerminalBinding
import r2u9.SimpleSSH.service.SshConnectionService
import r2u9.SimpleSSH.ssh.SshSession
import r2u9.SimpleSSH.terminal.TerminalEmulator
import r2u9.SimpleSSH.util.AppPreferences
import r2u9.SimpleSSH.util.BiometricHelper

class TerminalActivity : AppCompatActivity() {

    private lateinit var binding: ActivityTerminalBinding
    private var emulator: TerminalEmulator? = null

    private var sessionId: String? = null
    private var connectionName: String? = null
    private var themeName: String = "Default"

    private var sshService: SshConnectionService? = null
    private var serviceBound = false
    private var sshSession: SshSession? = null

    private var readJob: Job? = null
    private var ctrlPressed = false
    private var altPressed = false

    private lateinit var prefs: AppPreferences

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            val binder = service as SshConnectionService.LocalBinder
            sshService = binder.getService()
            serviceBound = true
            sessionId?.let { id ->
                sshSession = sshService?.getSession(id)
                if (sshSession != null) {
                    // Get or create emulator from service (persists across activity recreation)
                    emulator = sshService?.getOrCreateEmulator(id)
                    setupTerminalWithEmulator()
                    startReading()
                    binding.progressBar.visibility = View.GONE
                    binding.terminalView.showKeyboard()
                } else {
                    showError("Session not found")
                }
            }
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            sshService = null
            serviceBound = false
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivityTerminalBinding.inflate(layoutInflater)
        setContentView(binding.root)

        prefs = AppPreferences.getInstance(this)

        sessionId = intent.getStringExtra(EXTRA_SESSION_ID)
        connectionName = intent.getStringExtra(EXTRA_CONNECTION_NAME)
        // Use connection theme if provided, otherwise use default from settings
        themeName = intent.getStringExtra(EXTRA_THEME)?.takeIf { it.isNotEmpty() } ?: prefs.defaultTheme

        setupInsets()
        setupExtraKeys()

        // Hide toolbar - all settings are in Settings activity now
        binding.appBarLayout.visibility = View.GONE

        binding.progressBar.visibility = View.VISIBLE

        // Start and bind to the service to ensure it stays alive
        Intent(this, SshConnectionService::class.java).also { intent ->
            startService(intent)
            bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        val newSessionId = intent.getStringExtra(EXTRA_SESSION_ID)

        // If it's a different session, switch to it
        if (newSessionId != null && newSessionId != sessionId) {
            // Stop reading from old session
            readJob?.cancel()

            // Update session info
            sessionId = newSessionId
            connectionName = intent.getStringExtra(EXTRA_CONNECTION_NAME)
            themeName = intent.getStringExtra(EXTRA_THEME)?.takeIf { it.isNotEmpty() } ?: prefs.defaultTheme

            // Reconnect to the new session
            if (serviceBound) {
                sshSession = sshService?.getSession(newSessionId)
                if (sshSession != null) {
                    emulator = sshService?.getOrCreateEmulator(newSessionId)
                    setupTerminalWithEmulator()
                    startReading()
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        applySettings()
    }

    override fun onDestroy() {
        super.onDestroy()
        readJob?.cancel()
        if (serviceBound) {
            unbindService(serviceConnection)
            serviceBound = false
        }
    }

    private fun applySettings() {
        // Apply font size
        binding.terminalView.setTextSize(prefs.defaultFontSize.toFloat())

        // Apply extra keys visibility
        binding.extraKeysCard.visibility = if (prefs.showExtraKeys) View.VISIBLE else View.GONE
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        showExitDialog()
    }

    private fun showExitDialog() {
        MaterialAlertDialogBuilder(this)
            .setTitle("Close Terminal")
            .setMessage("Do you want to keep the session running in the background?")
            .setPositiveButton("Keep Running") { _, _ ->
                finish()
            }
            .setNegativeButton("Disconnect") { _, _ ->
                sessionId?.let { sshService?.disconnect(it) }
                finish()
            }
            .show()
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        when (keyCode) {
            KeyEvent.KEYCODE_VOLUME_UP -> {
                handleVolumeKeyAction(AppPreferences.VolumeKeyAction.fromName(prefs.volumeUpAction))
                return true
            }
            KeyEvent.KEYCODE_VOLUME_DOWN -> {
                handleVolumeKeyAction(AppPreferences.VolumeKeyAction.fromName(prefs.volumeDownAction))
                return true
            }
        }
        return super.onKeyDown(keyCode, event)
    }

    override fun onKeyUp(keyCode: Int, event: KeyEvent?): Boolean {
        when (keyCode) {
            KeyEvent.KEYCODE_VOLUME_UP, KeyEvent.KEYCODE_VOLUME_DOWN -> {
                return true
            }
        }
        return super.onKeyUp(keyCode, event)
    }

    private fun handleVolumeKeyAction(action: AppPreferences.VolumeKeyAction) {
        when (action) {
            AppPreferences.VolumeKeyAction.NONE -> {}
            AppPreferences.VolumeKeyAction.UP_ARROW -> sendInput("\u001b[A")
            AppPreferences.VolumeKeyAction.DOWN_ARROW -> sendInput("\u001b[B")
            AppPreferences.VolumeKeyAction.TAB -> sendInput("\t")
            AppPreferences.VolumeKeyAction.ENTER -> sendInput("\r")
            AppPreferences.VolumeKeyAction.CTRL -> {
                ctrlPressed = !ctrlPressed
                updateModifierKeyStates()
            }
            AppPreferences.VolumeKeyAction.ALT -> {
                altPressed = !altPressed
                updateModifierKeyStates()
            }
            AppPreferences.VolumeKeyAction.ESC -> sendInput("\u001b")
        }
    }

    private fun setupInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(binding.main) { _, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            val ime = insets.getInsets(WindowInsetsCompat.Type.ime())

            binding.appBarLayout.setPadding(systemBars.left, systemBars.top, systemBars.right, 0)
            binding.terminalContainer.setPadding(systemBars.left, systemBars.top, systemBars.right, 0)

            // Position extra keys above the keyboard
            val bottomPadding = if (ime.bottom > 0) ime.bottom else systemBars.bottom
            binding.extraKeysCard.translationY = -bottomPadding.toFloat()

            // Adjust terminal container bottom padding to account for extra keys
            val extraKeysHeight = binding.extraKeysCard.height
            binding.terminalContainer.setPadding(
                systemBars.left,
                systemBars.top,
                systemBars.right,
                extraKeysHeight + bottomPadding
            )

            insets
        }
    }

    private fun setupTerminalWithEmulator() {
        val emu = emulator ?: return

        val theme = TerminalTheme.getByName(themeName)
        emu.setTheme(theme)
        updateTerminalColors(theme)

        // Hook up PTY resize when terminal size changes
        // Note: resizePty is called from IO thread to avoid blocking UI
        emu.onSizeChanged = { cols, rows ->
            sshSession?.let { session ->
                lifecycleScope.launch(Dispatchers.IO) {
                    try {
                        session.resizePty(cols, rows)
                    } catch (e: Exception) {
                        // Ignore resize errors - connection might be closing
                    }
                }
            }
        }

        binding.terminalView.setEmulator(emu)
        binding.terminalView.setTextSize(prefs.defaultFontSize.toFloat())
        binding.terminalView.setOnKeyInput { input ->
            sendInput(processInput(input))
        }
        binding.terminalView.setOnLongPressListener { x, y ->
            showContextMenu(x, y)
        }
        binding.terminalView.setOnFontSizeChangedListener { newSize ->
            prefs.defaultFontSize = newSize.toInt()
        }
    }

    private fun showContextMenu(x: Float, y: Float) {
        val dialog = Dialog(this)
        dialog.setContentView(R.layout.dialog_terminal_menu)
        dialog.window?.apply {
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            setDimAmount(0.5f)
            setGravity(Gravity.CENTER)
            setLayout(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT
            )
        }

        val menuCopy = dialog.findViewById<View>(R.id.menuCopy)
        val menuPaste = dialog.findViewById<View>(R.id.menuPaste)
        val menuPastePassword = dialog.findViewById<View>(R.id.menuPastePassword)
        val menuSelectText = dialog.findViewById<View>(R.id.menuSelectText)
        val menuSettings = dialog.findViewById<View>(R.id.menuSettings)

        // Show copy option if there's a selection
        if (binding.terminalView.hasSelection()) {
            menuCopy.visibility = View.VISIBLE
        }

        // Show paste password option if connection uses password auth
        val activeSession = sessionId?.let { sshService?.getActiveSession(it) }
        if (activeSession?.connection?.authType == AuthType.PASSWORD &&
            !activeSession.connection.password.isNullOrEmpty()) {
            menuPastePassword.visibility = View.VISIBLE
        }

        menuCopy.setOnClickListener {
            dialog.dismiss()
            copySelectedText()
        }

        menuPaste.setOnClickListener {
            dialog.dismiss()
            pasteFromClipboard()
        }

        menuPastePassword.setOnClickListener {
            dialog.dismiss()
            pastePassword()
        }

        menuSelectText.setOnClickListener {
            dialog.dismiss()
            binding.terminalView.startSelection(x, y)
            Toast.makeText(this, "Drag to select text, tap to cancel", Toast.LENGTH_SHORT).show()
        }

        menuSettings.setOnClickListener {
            dialog.dismiss()
            startActivity(Intent(this, SettingsActivity::class.java))
        }

        dialog.show()
    }

    private fun copySelectedText() {
        val selectedText = binding.terminalView.getSelectedText()
        if (!selectedText.isNullOrEmpty()) {
            val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clip = ClipData.newPlainText("Terminal Text", selectedText)
            clipboard.setPrimaryClip(clip)
            Toast.makeText(this, "Copied to clipboard", Toast.LENGTH_SHORT).show()
        }
        binding.terminalView.clearSelection()
    }

    private fun pasteFromClipboard() {
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clipData = clipboard.primaryClip
        if (clipData != null && clipData.itemCount > 0) {
            val text = clipData.getItemAt(0).coerceToText(this).toString()
            if (text.isNotEmpty()) {
                sendInput(text)
            }
        }
    }

    private fun pastePassword() {
        BiometricHelper.authenticateIfEnabled(
            activity = this,
            prefs = prefs,
            title = "Authenticate",
            subtitle = "Verify your identity to paste password",
            onSuccess = {
                val activeSession = sessionId?.let { sshService?.getActiveSession(it) }
                val password = activeSession?.connection?.password
                if (!password.isNullOrEmpty()) {
                    sendInput(password)
                }
            },
            onError = { error ->
                Toast.makeText(this, "Authentication failed: $error", Toast.LENGTH_SHORT).show()
            }
        )
    }

    private fun processInput(input: String): String {
        var result = input

        if (ctrlPressed) {
            result = if (input.length == 1) {
                val char = input[0]
                if (char in 'a'..'z' || char in 'A'..'Z') {
                    ((char.uppercaseChar().code - 'A'.code + 1)).toChar().toString()
                } else {
                    input
                }
            } else {
                input
            }
            ctrlPressed = false
            updateModifierKeyStates()
        }

        if (altPressed) {
            result = "\u001b$result"
            altPressed = false
            updateModifierKeyStates()
        }

        return result
    }

    private fun setupExtraKeys() {
        binding.keyTab.setOnClickListener { sendInput("\t") }
        binding.keyEnter.setOnClickListener { sendInput("\r") }

        binding.keyCtrl.setOnClickListener {
            ctrlPressed = !ctrlPressed
            updateModifierKeyStates()
        }

        binding.keyAlt.setOnClickListener {
            altPressed = !altPressed
            updateModifierKeyStates()
        }

        binding.keyEsc.setOnClickListener { sendInput("\u001b") }
        binding.keyUp.setOnClickListener { sendInput("\u001b[A") }
        binding.keyDown.setOnClickListener { sendInput("\u001b[B") }
        binding.keyLeft.setOnClickListener { sendInput("\u001b[D") }
        binding.keyRight.setOnClickListener { sendInput("\u001b[C") }
        binding.keyHome.setOnClickListener { sendInput("\u001b[H") }
        binding.keyEnd.setOnClickListener { sendInput("\u001b[F") }
        binding.keyPgUp.setOnClickListener { sendInput("\u001b[5~") }
        binding.keyPgDn.setOnClickListener { sendInput("\u001b[6~") }
    }

    private fun updateModifierKeyStates() {
        binding.keyCtrl.isChecked = ctrlPressed
        binding.keyAlt.isChecked = altPressed
    }

    private fun startReading() {
        readJob = lifecycleScope.launch(Dispatchers.IO) {
            val session = sshSession ?: return@launch
            val buffer = ByteArray(8192)

            try {
                while (isActive && session.isConnected()) {
                    val available = session.inputStream.available()
                    if (available > 0) {
                        val bytesRead = session.inputStream.read(buffer, 0, minOf(available, buffer.size))
                        if (bytesRead > 0) {
                            val data = buffer.copyOf(bytesRead)
                            withContext(Dispatchers.Main) {
                                emulator?.processInput(data)
                            }
                        }
                    } else {
                        kotlinx.coroutines.delay(10)
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    if (!isFinishing) {
                        showError("Connection lost: ${e.message}")
                    }
                }
            }
        }
    }

    private fun sendInput(input: String) {
        val session = sshSession ?: return
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                session.outputStream.write(input.toByteArray())
                session.outputStream.flush()
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    if (!isFinishing) {
                        showError("Connection lost: ${e.message}")
                    }
                }
            }
        }
    }

    private fun updateTerminalColors(theme: TerminalTheme) {
        binding.terminalContainer.setBackgroundColor(theme.backgroundColor)
        @Suppress("DEPRECATION")
        window.statusBarColor = theme.backgroundColor
        @Suppress("DEPRECATION")
        window.navigationBarColor = theme.backgroundColor
    }

    private fun showError(message: String) {
        MaterialAlertDialogBuilder(this)
            .setTitle("Error")
            .setMessage(message)
            .setPositiveButton("OK") { _, _ ->
                finish()
            }
            .setCancelable(false)
            .show()
    }

    companion object {
        const val EXTRA_SESSION_ID = "session_id"
        const val EXTRA_CONNECTION_NAME = "connection_name"
        const val EXTRA_THEME = "theme"
    }
}
