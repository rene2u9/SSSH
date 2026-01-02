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
import androidx.activity.OnBackPressedCallback
import android.view.View
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
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
import r2u9.SimpleSSH.terminal.ExtraKeysView
import r2u9.SimpleSSH.terminal.TerminalEmulator
import r2u9.SimpleSSH.util.AppPreferences
import r2u9.SimpleSSH.util.BiometricHelper

class TerminalActivity : BaseActivity() {

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

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            val binder = service as SshConnectionService.LocalBinder
            sshService = binder.getService()
            serviceBound = true
            sessionId?.let { id ->
                sshSession = sshService?.getSession(id)
                if (sshSession != null) {
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

        sessionId = intent.getStringExtra(EXTRA_SESSION_ID)
        connectionName = intent.getStringExtra(EXTRA_CONNECTION_NAME)
        themeName = intent.getStringExtra(EXTRA_THEME)?.takeIf { it.isNotEmpty() } ?: prefs.defaultTheme

        setupInsets()
        setupExtraKeys()

        binding.appBarLayout.visibility = View.GONE

        binding.progressBar.visibility = View.VISIBLE

        Intent(this, SshConnectionService::class.java).also { intent ->
            startService(intent)
            bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)
        }

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                showExitDialog()
            }
        })
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        val newSessionId = intent.getStringExtra(EXTRA_SESSION_ID)

        if (newSessionId != null && newSessionId != sessionId) {
            readJob?.cancel()

            sessionId = newSessionId
            connectionName = intent.getStringExtra(EXTRA_CONNECTION_NAME)
            themeName = intent.getStringExtra(EXTRA_THEME)?.takeIf { it.isNotEmpty() } ?: prefs.defaultTheme

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
        binding.terminalView.setTextSize(prefs.defaultFontSize.toFloat())
        binding.extraKeysView.visibility = if (prefs.showExtraKeys) View.VISIBLE else View.GONE
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
            }
            AppPreferences.VolumeKeyAction.ALT -> {
                altPressed = !altPressed
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

            val bottomPadding = if (ime.bottom > 0) ime.bottom else systemBars.bottom
            binding.extraKeysView.translationY = -bottomPadding.toFloat()

            val extraKeysHeight = binding.extraKeysView.height
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

        emu.onSizeChanged = { cols, rows ->
            sshSession?.let { session ->
                lifecycleScope.launch(Dispatchers.IO) {
                    try {
                        session.resizePty(cols, rows)
                    } catch (_: Exception) {
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
        val menuChangeTheme = dialog.findViewById<View>(R.id.menuChangeTheme)
        val menuSettings = dialog.findViewById<View>(R.id.menuSettings)

        if (binding.terminalView.hasSelection()) {
            menuCopy.visibility = View.VISIBLE
        }

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

        menuChangeTheme.setOnClickListener {
            dialog.dismiss()
            showThemeDialog()
        }

        menuSettings.setOnClickListener {
            dialog.dismiss()
            startActivity(Intent(this, SettingsActivity::class.java))
        }

        dialog.show()
    }

    private fun showThemeDialog() {
        val themes = TerminalTheme.ALL_THEMES.map { it.name }.toTypedArray()
        val currentIndex = themes.indexOfFirst { it.equals(themeName, ignoreCase = true) }

        MaterialAlertDialogBuilder(this)
            .setTitle("Terminal Theme")
            .setSingleChoiceItems(themes, currentIndex) { dialog, which ->
                themeName = themes[which]
                val theme = TerminalTheme.getByName(themeName)
                emulator?.setTheme(theme)
                updateTerminalColors(theme)
                binding.terminalView.invalidate()
                dialog.dismiss()
            }
            .setNegativeButton("Cancel", null)
            .show()
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
            binding.extraKeysView.resetModifiers()
        }

        if (altPressed) {
            result = "\u001b$result"
            altPressed = false
            binding.extraKeysView.resetModifiers()
        }

        return result
    }

    private fun setupExtraKeys() {
        binding.extraKeysView.keyListener = object : ExtraKeysView.OnKeyListener {
            override fun onKey(key: String, code: Int) {
                sendInput(key)
            }

            override fun onModifierChanged(ctrl: Boolean, alt: Boolean, shift: Boolean, fn: Boolean) {
                ctrlPressed = ctrl
                altPressed = alt
            }
        }
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
