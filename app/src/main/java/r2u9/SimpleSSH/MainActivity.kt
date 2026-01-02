package r2u9.SimpleSSH

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.view.View
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import r2u9.SimpleSSH.ui.BaseActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import r2u9.SimpleSSH.data.model.ActiveSession
import r2u9.SimpleSSH.data.model.AuthType
import r2u9.SimpleSSH.data.model.SshConnection
import r2u9.SimpleSSH.data.model.TerminalTheme
import r2u9.SimpleSSH.databinding.ActivityMainBinding
import r2u9.SimpleSSH.databinding.DialogAddConnectionBinding
import r2u9.SimpleSSH.service.SshConnectionService
import r2u9.SimpleSSH.ui.TerminalActivity
import r2u9.SimpleSSH.ui.SettingsActivity
import r2u9.SimpleSSH.ui.adapter.ActiveSessionAdapter
import r2u9.SimpleSSH.ui.adapter.ConnectionAdapter
import r2u9.SimpleSSH.ui.adapter.HostStatus
import r2u9.SimpleSSH.ui.viewmodel.MainViewModel
import r2u9.SimpleSSH.util.BiometricHelper
import r2u9.SimpleSSH.util.ConfigExporter
import r2u9.SimpleSSH.util.ShortcutHelper
import r2u9.SimpleSSH.util.Constants
import r2u9.SimpleSSH.util.WakeOnLan
import r2u9.SimpleSSH.util.isHostReachable

class MainActivity : BaseActivity() {

    private lateinit var binding: ActivityMainBinding
    private val viewModel: MainViewModel by viewModels()

    private lateinit var connectionAdapter: ConnectionAdapter
    private lateinit var activeSessionAdapter: ActiveSessionAdapter

    private var sshService: SshConnectionService? = null
    private var serviceBound = false
    private var sessionsJob: kotlinx.coroutines.Job? = null

    private var pendingKeyContent: String? = null
    private var pendingConnectionId: Long? = null

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            val binder = service as SshConnectionService.LocalBinder
            sshService = binder.getService()
            serviceBound = true
            observeSessionsFlow()
            pendingConnectionId?.let { handleShortcutConnection(it) }
            pendingConnectionId = null
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            sshService = null
            serviceBound = false
            sessionsJob?.cancel()
        }
    }

    private fun observeSessionsFlow() {
        sessionsJob?.cancel()
        sessionsJob = lifecycleScope.launch {
            sshService?.sessionsFlow?.collect { sessions ->
                viewModel.updateActiveSessions(sessions)
                binding.activeSessionCount.text = "${sessions.size} connected"
                activeSessionAdapter.submitList(sessions)
                updateActiveSessionsCard(sessions.isNotEmpty())
            }
        }
    }

    private val keyFileLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let { loadPrivateKey(it) }
    }

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { _ -> }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        ViewCompat.setOnApplyWindowInsetsListener(binding.main) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        setupToolbar()
        setupRecyclerViews()
        setupFab()
        setupSwipeRefresh()
        observeData()

        if (savedInstanceState == null) {
            requestNotificationPermission()
        }

        Intent(this, SshConnectionService::class.java).also { intent ->
            startService(intent)
            bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)
        }

        handleIntent(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        if (intent?.action == ShortcutHelper.ACTION_CONNECT) {
            val connectionId = intent.getLongExtra(ShortcutHelper.EXTRA_CONNECTION_ID, -1)
            if (connectionId != -1L) {
                if (serviceBound) {
                    handleShortcutConnection(connectionId)
                } else {
                    pendingConnectionId = connectionId
                }
            }
        }
    }

    private fun handleShortcutConnection(connectionId: Long) {
        lifecycleScope.launch {
            val connection = viewModel.getConnectionById(connectionId)
            if (connection != null) {
                connectTo(connection)
            } else {
                showError("Connection not found")
            }
        }
    }

    override fun onResume() {
        super.onResume()
        if (connectionAdapter.currentList.isNotEmpty()) {
            refreshHostStatus()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        sessionsJob?.cancel()
        if (serviceBound) {
            unbindService(serviceConnection)
            serviceBound = false
        }
    }

    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    private fun setupToolbar() {
        binding.toolbar.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                R.id.action_settings -> {
                    startActivity(Intent(this, SettingsActivity::class.java))
                    true
                }
                else -> false
            }
        }
    }

    private fun setupRecyclerViews() {
        connectionAdapter = ConnectionAdapter(
            onConnect = { connection -> connectTo(connection) },
            onEdit = { connection -> showEditConnectionDialog(connection) },
            onDuplicate = { connection -> showDuplicateConnectionDialog(connection) },
            onDelete = { connection -> showDeleteConfirmation(connection) }
        )
        binding.connectionsList.apply {
            layoutManager = LinearLayoutManager(this@MainActivity)
            adapter = connectionAdapter
        }

        activeSessionAdapter = ActiveSessionAdapter(
            onOpen = { session ->
                openTerminal(session.sessionId, session.connection)
            },
            onDisconnect = { session ->
                sshService?.disconnect(session.sessionId)
            }
        )
        binding.activeSessionsList.apply {
            layoutManager = LinearLayoutManager(this@MainActivity)
            adapter = activeSessionAdapter
        }
    }

    private fun setupFab() {
        binding.fabAdd.setOnClickListener {
            showAddConnectionDialog()
        }
        binding.emptyAddButton.setOnClickListener {
            showAddConnectionDialog()
        }
    }

    private fun setupSwipeRefresh() {
        binding.swipeRefresh.setColorSchemeResources(R.color.primary)
        binding.swipeRefresh.setProgressBackgroundColorSchemeResource(R.color.surface_container_high)
        binding.swipeRefresh.setOnRefreshListener {
            refreshHostStatus()
        }
        binding.refreshButton.setOnClickListener {
            refreshHostStatus()
        }
    }

    private fun refreshHostStatus() {
        binding.swipeRefresh.isRefreshing = true
        connectionAdapter.resetAllStatus()

        val connections = connectionAdapter.currentList
        if (connections.isEmpty()) {
            binding.swipeRefresh.isRefreshing = false
            return
        }

        lifecycleScope.launch {
            connections.forEach { connection ->
                launch {
                    val isReachable = isHostReachable(connection.host, connection.port)
                    val status = when {
                        isReachable -> HostStatus.ONLINE
                        connection.wolEnabled && !connection.wolMacAddress.isNullOrEmpty() -> HostStatus.WOL_AVAILABLE
                        else -> HostStatus.OFFLINE
                    }
                    withContext(Dispatchers.Main) {
                        connectionAdapter.updateHostStatus(connection.id, status)
                    }
                }
            }
            withContext(Dispatchers.Main) {
                binding.swipeRefresh.isRefreshing = false
            }
        }
    }

    private fun observeData() {
        lifecycleScope.launch {
            viewModel.connections.collectLatest { connections ->
                connectionAdapter.submitList(connections) {
                    updateEmptyState(connections.isEmpty())
                    if (connections.isNotEmpty()) {
                        refreshHostStatus()
                    }
                }
                ShortcutHelper.updateShortcuts(this@MainActivity, connections)
            }
        }

    }

    private fun updateEmptyState(isEmpty: Boolean) {
        binding.emptyState.visibility = if (isEmpty) View.VISIBLE else View.GONE
        binding.connectionsList.visibility = if (isEmpty) View.GONE else View.VISIBLE
    }

    private fun updateActiveSessionsCard(hasActiveSessions: Boolean) {
        binding.activeSessionsCard.visibility = if (hasActiveSessions) View.VISIBLE else View.GONE
    }

    private fun connectTo(connection: SshConnection) {
        BiometricHelper.authenticateIfEnabled(
            activity = this,
            prefs = prefs,
            title = "Authenticate",
            subtitle = "Verify your identity to connect",
            onSuccess = {
                val existingSession = sshService?.findExistingSession(connection)
                if (existingSession != null) {
                    showExistingSessionDialog(existingSession, connection)
                } else {
                    performConnection(connection)
                }
            },
            onError = { error ->
                showError("Authentication failed: $error")
            }
        )
    }

    private fun showExistingSessionDialog(existingSession: ActiveSession, connection: SshConnection) {
        MaterialAlertDialogBuilder(this)
            .setTitle("Session Already Active")
            .setMessage("A session to ${connection.username}@${connection.host}:${connection.port} is already active.\n\nWould you like to return to the existing session or create a new one?")
            .setPositiveButton("Return to Session") { _, _ ->
                openTerminal(existingSession.sessionId, existingSession.connection)
            }
            .setNegativeButton("New Session") { _, _ ->
                performConnection(connection)
            }
            .setNeutralButton("Cancel", null)
            .show()
    }

    private fun performConnection(connection: SshConnection) {
        lifecycleScope.launch {
            val loadingDialog = MaterialAlertDialogBuilder(this@MainActivity)
                .setTitle("Connecting")
                .setMessage(if (connection.wolEnabled) "Sending Wake-on-LAN packet..." else "Connecting to ${connection.host}...")
                .setCancelable(false)
                .create()
            loadingDialog.show()

            try {
                if (connection.wolEnabled && !connection.wolMacAddress.isNullOrEmpty()) {
                    val wolResult = WakeOnLan.sendMagicPacket(
                        macAddress = connection.wolMacAddress,
                        broadcastAddress = connection.wolBroadcastAddress ?: "255.255.255.255",
                        port = connection.wolPort
                    )
                    wolResult.onFailure { error ->
                        loadingDialog.dismiss()
                        showError("Wake-on-LAN failed: ${error.message}")
                        return@launch
                    }
                    loadingDialog.setMessage("Waiting for ${connection.host} to wake up...")
                    kotlinx.coroutines.delay(Constants.Time.WOL_BOOT_DELAY_MS)
                }

                loadingDialog.setMessage("Connecting to ${connection.host}...")
                val result = sshService?.connect(connection)
                loadingDialog.dismiss()

                result?.fold(
                    onSuccess = { sessionId ->
                        viewModel.updateLastConnected(connection.id)
                        openTerminal(sessionId, connection)
                    },
                    onFailure = { error ->
                        showError("Connection failed: ${error.message}")
                    }
                ) ?: showError("Service not available")
            } catch (e: Exception) {
                loadingDialog.dismiss()
                showError("Connection failed: ${e.message}")
            }
        }
    }

    private fun openTerminal(sessionId: String, connection: SshConnection) {
        val intent = Intent(this, TerminalActivity::class.java).apply {
            putExtra(TerminalActivity.EXTRA_SESSION_ID, sessionId)
            putExtra(TerminalActivity.EXTRA_CONNECTION_NAME, connection.name)
            putExtra(TerminalActivity.EXTRA_THEME, connection.colorTheme)
        }
        startActivity(intent)
    }

    private fun showAddConnectionDialog(existingConnection: SshConnection? = null) {
        val dialogBinding = DialogAddConnectionBinding.inflate(layoutInflater)
        val isEditing = existingConnection != null && existingConnection.id != 0L

        val themes = TerminalTheme.ALL_THEMES.map { it.name }
        val themeAdapter = ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, themes)
        (dialogBinding.themeDropdown as? AutoCompleteTextView)?.setAdapter(themeAdapter)
        dialogBinding.themeDropdown.setText(prefs.defaultTheme, false)

        existingConnection?.let { conn ->
            dialogBinding.nameInput.setText(conn.name)
            dialogBinding.hostInput.setText(conn.host)
            dialogBinding.portInput.setText(conn.port.toString())
            dialogBinding.usernameInput.setText(conn.username)
            dialogBinding.themeDropdown.setText(conn.colorTheme, false)

            if (conn.authType == AuthType.KEY) {
                dialogBinding.authToggle.check(R.id.authKey)
                dialogBinding.passwordLayout.visibility = View.GONE
                dialogBinding.keySection.visibility = View.VISIBLE
                pendingKeyContent = conn.privateKey
                dialogBinding.keyFileName.text = "Private key loaded"
                dialogBinding.keyFileName.visibility = View.VISIBLE
                dialogBinding.keyPassphraseInput.setText(conn.privateKeyPassphrase)
            } else {
                dialogBinding.authToggle.check(R.id.authPassword)
                dialogBinding.passwordInput.setText(conn.password)
            }

            dialogBinding.wolSwitch.isChecked = conn.wolEnabled
            dialogBinding.wolSection.visibility = if (conn.wolEnabled) View.VISIBLE else View.GONE
            dialogBinding.wolMacInput.setText(conn.wolMacAddress ?: "")
            dialogBinding.wolBroadcastInput.setText(conn.wolBroadcastAddress ?: "")
            dialogBinding.wolPortInput.setText(conn.wolPort.toString())
        }

        dialogBinding.wolSwitch.setOnCheckedChangeListener { _, isChecked ->
            dialogBinding.wolSection.visibility = if (isChecked) View.VISIBLE else View.GONE
        }

        dialogBinding.authToggle.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (isChecked) {
                when (checkedId) {
                    R.id.authPassword -> {
                        dialogBinding.passwordLayout.visibility = View.VISIBLE
                        dialogBinding.keySection.visibility = View.GONE
                    }
                    R.id.authKey -> {
                        dialogBinding.passwordLayout.visibility = View.GONE
                        dialogBinding.keySection.visibility = View.VISIBLE
                    }
                }
            }
        }

        dialogBinding.selectKeyButton.setOnClickListener {
            keyFileLauncher.launch(arrayOf("*/*"))
        }

        val dialog = MaterialAlertDialogBuilder(this)
            .setTitle(if (isEditing) "Edit Connection" else "New Connection")
            .setView(dialogBinding.root)
            .setPositiveButton(if (isEditing) "Save" else "Add", null)
            .setNegativeButton("Cancel", null)
            .create()

        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val name = dialogBinding.nameInput.text.toString().trim()
                val host = dialogBinding.hostInput.text.toString().trim()
                val port = dialogBinding.portInput.text.toString().toIntOrNull() ?: 22
                val username = dialogBinding.usernameInput.text.toString().trim()
                val theme = dialogBinding.themeDropdown.text.toString()

                val isKeyAuth = dialogBinding.authToggle.checkedButtonId == R.id.authKey
                val password = if (!isKeyAuth) dialogBinding.passwordInput.text.toString() else null
                val privateKey = if (isKeyAuth) pendingKeyContent else null
                val keyPassphrase = if (isKeyAuth) dialogBinding.keyPassphraseInput.text.toString().takeIf { it.isNotEmpty() } else null
                val wolEnabled = dialogBinding.wolSwitch.isChecked
                val wolMac = dialogBinding.wolMacInput.text.toString().trim()
                val wolBroadcast = dialogBinding.wolBroadcastInput.text.toString().trim().takeIf { it.isNotEmpty() }
                val wolPort = dialogBinding.wolPortInput.text.toString().toIntOrNull() ?: 9

                if (name.isEmpty()) {
                    dialogBinding.nameLayout.error = "Name is required"
                    return@setOnClickListener
                }
                if (host.isEmpty()) {
                    dialogBinding.hostLayout.error = "Host is required"
                    return@setOnClickListener
                }
                if (username.isEmpty()) {
                    dialogBinding.usernameLayout.error = "Username is required"
                    return@setOnClickListener
                }
                if (!isKeyAuth && password.isNullOrEmpty()) {
                    dialogBinding.passwordLayout.error = "Password is required"
                    return@setOnClickListener
                }
                if (isKeyAuth && privateKey == null) {
                    Toast.makeText(this, "Please select a private key", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
                if (wolEnabled && wolMac.isEmpty()) {
                    dialogBinding.wolMacLayout.error = "MAC address is required for WOL"
                    return@setOnClickListener
                }
                if (wolEnabled && !WakeOnLan.isValidMacAddress(wolMac)) {
                    dialogBinding.wolMacLayout.error = "Invalid MAC address format"
                    return@setOnClickListener
                }

                val connection = SshConnection(
                    id = existingConnection?.id ?: 0,
                    name = name,
                    host = host,
                    port = port,
                    username = username,
                    authType = if (isKeyAuth) AuthType.KEY else AuthType.PASSWORD,
                    password = password,
                    privateKey = privateKey,
                    privateKeyPassphrase = keyPassphrase,
                    colorTheme = theme,
                    createdAt = existingConnection?.createdAt ?: System.currentTimeMillis(),
                    lastConnectedAt = existingConnection?.lastConnectedAt,
                    wolEnabled = wolEnabled,
                    wolMacAddress = if (wolEnabled && wolMac.isNotEmpty()) WakeOnLan.formatMacAddress(wolMac) else null,
                    wolBroadcastAddress = if (wolEnabled) wolBroadcast else null,
                    wolPort = wolPort
                )

                if (isEditing) {
                    viewModel.updateConnection(connection)
                } else {
                    viewModel.addConnection(connection)
                }

                pendingKeyContent = null
                dialog.dismiss()
            }
        }

        dialog.show()
    }

    private fun showEditConnectionDialog(connection: SshConnection) {
        showAddConnectionDialog(connection)
    }

    private fun showDuplicateConnectionDialog(connection: SshConnection) {
        val duplicatedConnection = connection.copy(
            id = 0,
            name = "${connection.name} (copy)",
            createdAt = System.currentTimeMillis(),
            lastConnectedAt = null
        )
        showAddConnectionDialog(duplicatedConnection)
    }

    private fun showDeleteConfirmation(connection: SshConnection) {
        MaterialAlertDialogBuilder(this)
            .setTitle("Delete Connection")
            .setMessage("Are you sure you want to delete \"${connection.name}\"?")
            .setPositiveButton("Delete") { _, _ ->
                viewModel.deleteConnection(connection)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun loadPrivateKey(uri: Uri) {
        try {
            pendingKeyContent = ConfigExporter.readFromUri(this, uri)
            Toast.makeText(this, "Private key loaded", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            showError("Failed to load key: ${e.message}")
        }
    }

    private fun showError(message: String) {
        Snackbar.make(binding.root, message, Snackbar.LENGTH_LONG).show()
    }
}
