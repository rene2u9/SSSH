package r2u9.SimpleSSH.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Color
import android.os.Binder
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import r2u9.SimpleSSH.MainActivity
import r2u9.SimpleSSH.R
import r2u9.SimpleSSH.data.model.ActiveSession
import r2u9.SimpleSSH.data.model.SshConnection
import r2u9.SimpleSSH.ssh.SshManager
import r2u9.SimpleSSH.ssh.SshSession
import r2u9.SimpleSSH.terminal.TerminalEmulator
import r2u9.SimpleSSH.ui.TerminalActivity
import r2u9.SimpleSSH.util.AppPreferences
import r2u9.SimpleSSH.util.Logger

class SshConnectionService : Service() {

    private val binder = LocalBinder()
    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private val activeSessions = mutableMapOf<String, ActiveSession>()
    private val sessionEmulators = mutableMapOf<String, TerminalEmulator>()
    private val sessionNotificationIds = mutableMapOf<String, Int>()
    private var nextNotificationId = NOTIFICATION_ID_BASE + 1
    private var updateJob: Job? = null

    private val _sessionsFlow = MutableStateFlow<List<ActiveSession>>(emptyList())
    val sessionsFlow: StateFlow<List<ActiveSession>> = _sessionsFlow.asStateFlow()

    private fun notifySessionsChanged() {
        _sessionsFlow.value = activeSessions.values.toList()
    }

    inner class LocalBinder : Binder() {
        fun getService(): SshConnectionService = this@SshConnectionService
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannels()
    }

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_DISCONNECT_ALL -> {
                disconnectAll()
            }
            ACTION_DISCONNECT_SESSION -> {
                val sessionId = intent.getStringExtra(EXTRA_SESSION_ID)
                sessionId?.let { disconnect(it) }
            }
            ACTION_OPEN_SESSION -> {
                val sessionId = intent.getStringExtra(EXTRA_SESSION_ID)
                sessionId?.let { openSession(it) }
            }
            else -> {
                createNotificationChannels()
            }
        }
        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
        updateJob?.cancel()
        cancelAllSessionNotifications()
        SshManager.closeAllSessions()
    }

    private fun createNotificationChannels() {
        val notificationManager = getSystemService(NotificationManager::class.java)

        val serviceChannel = NotificationChannel(
            CHANNEL_ID_SERVICE,
            "SSH Service",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Background service for SSH connections"
            setShowBadge(false)
        }
        notificationManager.createNotificationChannel(serviceChannel)

        val sessionsChannel = NotificationChannel(
            CHANNEL_ID_SESSIONS,
            "Active Sessions",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Notifications for active SSH sessions"
            setShowBadge(true)
            enableLights(true)
            lightColor = Color.GREEN
        }
        notificationManager.createNotificationChannel(sessionsChannel)
    }

    suspend fun connect(connection: SshConnection): Result<String> {
        Logger.d(TAG, "connect() called for ${connection.host}")
        try {
            Logger.d(TAG, "Starting foreground service...")
            startForegroundWithPlaceholder()
            Logger.d(TAG, "Foreground service started successfully")
        } catch (e: Exception) {
            Logger.e(TAG, "Failed to start foreground service", e)
            return Result.failure(e)
        }

        Logger.d(TAG, "Attempting SSH connection...")
        val result = SshManager.connect(connection)
        return result.fold(
            onSuccess = { session ->
                Logger.d(TAG, "SSH connection successful, session id: ${session.id}")
                val activeSession = ActiveSession(
                    sessionId = session.id,
                    connection = connection
                )
                activeSessions[session.id] = activeSession

                sessionNotificationIds[session.id] = nextNotificationId++

                updateServiceNotification()
                showSessionNotification(activeSession)
                startNotificationUpdates()
                notifySessionsChanged()
                Result.success(session.id)
            },
            onFailure = { error ->
                Logger.e(TAG, "SSH connection failed", error)
                if (activeSessions.isEmpty()) {
                    stopForeground(STOP_FOREGROUND_REMOVE)
                }
                Result.failure(error)
            }
        )
    }

    private fun startForegroundWithPlaceholder() {
        val notification = NotificationCompat.Builder(this, CHANNEL_ID_SERVICE)
            .setSmallIcon(R.drawable.ic_terminal)
            .setContentTitle("Connecting...")
            .setContentText("Establishing SSH connection")
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setCategory(Notification.CATEGORY_SERVICE)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .build()
        ServiceCompat.startForeground(
            this,
            NOTIFICATION_ID_SERVICE,
            notification,
            ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
        )
    }

    private fun openSession(sessionId: String) {
        val session = activeSessions[sessionId] ?: return
        val intent = Intent(this, TerminalActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra(TerminalActivity.EXTRA_SESSION_ID, sessionId)
            putExtra(TerminalActivity.EXTRA_CONNECTION_NAME, session.connection.name)
            putExtra(TerminalActivity.EXTRA_THEME, session.connection.colorTheme)
        }
        startActivity(intent)
    }

    fun disconnect(sessionId: String) {
        SshManager.closeSession(sessionId)
        activeSessions.remove(sessionId)
        sessionEmulators.remove(sessionId)
        cancelSessionNotification(sessionId)
        sessionNotificationIds.remove(sessionId)
        updateServiceNotification()
        notifySessionsChanged()
        if (activeSessions.isEmpty()) {
            stopNotificationUpdates()
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
    }

    fun disconnectAll() {
        SshManager.closeAllSessions()
        cancelAllSessionNotifications()
        activeSessions.clear()
        sessionEmulators.clear()
        sessionNotificationIds.clear()
        stopNotificationUpdates()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
        notifySessionsChanged()
    }

    fun getSession(sessionId: String): SshSession? = SshManager.getSession(sessionId)

    fun getActiveSession(sessionId: String): ActiveSession? = activeSessions[sessionId]

    fun getAllActiveSessions(): List<ActiveSession> = activeSessions.values.toList()

    fun findExistingSession(connection: SshConnection): ActiveSession? {
        return activeSessions.values.find { session ->
            session.connection.host == connection.host &&
            session.connection.port == connection.port &&
            session.connection.username == connection.username
        }
    }

    fun getOrCreateEmulator(sessionId: String): TerminalEmulator {
        return sessionEmulators.getOrPut(sessionId) {
            val prefs = AppPreferences.getInstance(this)
            TerminalEmulator(80, 24, prefs.scrollbackLines)
        }
    }

    fun getEmulator(sessionId: String): TerminalEmulator? = sessionEmulators[sessionId]

    fun getActiveSessionCount(): Int = activeSessions.size

    private fun startNotificationUpdates() {
        if (updateJob != null) return
        updateJob = serviceScope.launch {
            while (isActive) {
                updateAllNotifications()
                delay(1000)
            }
        }
    }

    private fun stopNotificationUpdates() {
        updateJob?.cancel()
        updateJob = null
    }

    private fun updateAllNotifications() {
        if (activeSessions.isEmpty()) return
        updateServiceNotification()
        activeSessions.values.forEach { session ->
            showSessionNotification(session)
        }
    }

    private fun updateServiceNotification() {
        if (activeSessions.isEmpty()) return

        val notification = buildServiceNotification()
        ServiceCompat.startForeground(
            this,
            NOTIFICATION_ID_SERVICE,
            notification,
            ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
        )
    }

    private fun buildServiceNotification(): Notification {
        val mainIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val mainPendingIntent = PendingIntent.getActivity(
            this, 0, mainIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val disconnectIntent = Intent(this, SshConnectionService::class.java).apply {
            action = ACTION_DISCONNECT_ALL
        }
        val disconnectPendingIntent = PendingIntent.getService(
            this, 1, disconnectIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val sessionCount = activeSessions.size
        val title = "SimpleSSH"
        val content = if (sessionCount == 1) {
            "1 active connection"
        } else {
            "$sessionCount active connections"
        }

        return NotificationCompat.Builder(this, CHANNEL_ID_SERVICE)
            .setSmallIcon(R.drawable.ic_terminal)
            .setContentTitle(title)
            .setContentText(content)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(mainPendingIntent)
            .addAction(0, "Disconnect All", disconnectPendingIntent)
            .setCategory(Notification.CATEGORY_SERVICE)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setGroup(GROUP_KEY)
            .setGroupSummary(true)
            .build()
    }

    private fun showSessionNotification(session: ActiveSession) {
        val notificationId = sessionNotificationIds[session.sessionId] ?: return
        val notificationManager = getSystemService(NotificationManager::class.java)

        val openIntent = Intent(this, TerminalActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra(TerminalActivity.EXTRA_SESSION_ID, session.sessionId)
            putExtra(TerminalActivity.EXTRA_CONNECTION_NAME, session.connection.name)
            putExtra(TerminalActivity.EXTRA_THEME, session.connection.colorTheme)
        }
        val openPendingIntent = PendingIntent.getActivity(
            this, notificationId * 10, openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val disconnectIntent = Intent(this, SshConnectionService::class.java).apply {
            action = ACTION_DISCONNECT_SESSION
            putExtra(EXTRA_SESSION_ID, session.sessionId)
        }
        val disconnectPendingIntent = PendingIntent.getService(
            this, notificationId * 10 + 1, disconnectIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val connection = session.connection
        val title = connection.name
        val subtitle = "${connection.username}@${connection.host}:${connection.port}"
        val duration = session.getFormattedDuration()

        val notification = NotificationCompat.Builder(this, CHANNEL_ID_SESSIONS)
            .setSmallIcon(R.drawable.ic_terminal)
            .setContentTitle(title)
            .setContentText("$subtitle • $duration")
            .setSubText("Connected")
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(openPendingIntent)
            .addAction(0, "Open", openPendingIntent)
            .addAction(0, "Disconnect", disconnectPendingIntent)
            .setCategory(Notification.CATEGORY_SERVICE)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setGroup(GROUP_KEY)
            .setWhen(session.connectedAt)
            .setShowWhen(true)
            .setUsesChronometer(true)
            .setChronometerCountDown(false)
            .setColor(Color.parseColor("#4CAF50"))
            .build()

        notificationManager.notify(notificationId, notification)
    }

    private fun cancelSessionNotification(sessionId: String) {
        val notificationId = sessionNotificationIds[sessionId] ?: return
        val notificationManager = getSystemService(NotificationManager::class.java)
        notificationManager.cancel(notificationId)
    }

    private fun cancelAllSessionNotifications() {
        val notificationManager = getSystemService(NotificationManager::class.java)
        sessionNotificationIds.values.forEach { notificationId ->
            notificationManager.cancel(notificationId)
        }
    }

    companion object {
        private const val TAG = "SshConnectionService"
        const val CHANNEL_ID_SERVICE = "ssh_service"
        const val CHANNEL_ID_SESSIONS = "ssh_sessions"
        const val NOTIFICATION_ID_SERVICE = 1
        const val NOTIFICATION_ID_BASE = 100
        const val GROUP_KEY = "r2u9.SimpleSSH.SSH_SESSIONS"
        const val ACTION_DISCONNECT_ALL = "r2u9.SimpleSSH.DISCONNECT_ALL"
        const val ACTION_DISCONNECT_SESSION = "r2u9.SimpleSSH.DISCONNECT_SESSION"
        const val ACTION_OPEN_SESSION = "r2u9.SimpleSSH.OPEN_SESSION"
        const val EXTRA_SESSION_ID = "session_id"
    }
}
