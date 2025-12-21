package r2u9.SimpleSSH.ssh

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import net.schmizz.sshj.SSHClient
import net.schmizz.sshj.connection.channel.direct.Session
import net.schmizz.sshj.transport.verification.PromiscuousVerifier
import org.bouncycastle.jce.provider.BouncyCastleProvider
import r2u9.SimpleSSH.data.model.AuthType
import r2u9.SimpleSSH.data.model.SshConnection
import java.io.InputStream
import java.io.OutputStream
import java.security.Security
import java.util.UUID

class SshSession(
    val id: String,
    val client: SSHClient,
    val session: Session,
    val shell: Session.Shell,
    val inputStream: InputStream,
    val outputStream: OutputStream
) {
    fun close() {
        try {
            shell.close()
            session.close()
            client.disconnect()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun isConnected(): Boolean {
        return client.isConnected && shell.isOpen
    }

    fun resizePty(cols: Int, rows: Int) {
        try {
            Log.d("SshSession", "Resizing PTY to ${cols}x${rows}")
            shell.changeWindowDimensions(cols, rows, 0, 0)
            Log.d("SshSession", "PTY resize successful")
        } catch (e: Exception) {
            Log.e("SshSession", "Failed to resize PTY: ${e.message}", e)
        }
    }
}

object SshManager {
    private const val TAG = "SshManager"
    private val activeSessions = mutableMapOf<String, SshSession>()

    init {
        Security.removeProvider(BouncyCastleProvider.PROVIDER_NAME)
        Security.insertProviderAt(BouncyCastleProvider(), 1)
    }

    suspend fun connect(connection: SshConnection): Result<SshSession> = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Creating SSH client...")
            val client = SSHClient()
            client.addHostKeyVerifier(PromiscuousVerifier())

            Log.d(TAG, "Connecting to ${connection.host}:${connection.port}...")
            client.connect(connection.host, connection.port)
            Log.d(TAG, "Connected to server, authenticating...")

            when (connection.authType) {
                AuthType.PASSWORD -> {
                    Log.d(TAG, "Using password authentication for user: ${connection.username}")
                    client.authPassword(connection.username, connection.password ?: "")
                }
                AuthType.KEY -> {
                    Log.d(TAG, "Using key authentication for user: ${connection.username}")
                    val keyProvider = if (connection.privateKeyPassphrase.isNullOrEmpty()) {
                        client.loadKeys(connection.privateKey)
                    } else {
                        client.loadKeys(connection.privateKey, connection.privateKeyPassphrase)
                    }
                    client.authPublickey(connection.username, keyProvider)
                }
            }
            Log.d(TAG, "Authentication successful")

            Log.d(TAG, "Starting session and allocating PTY...")
            val session = client.startSession()
            session.allocatePTY("xterm-256color", 80, 24, 0, 0, emptyMap())
            val shell = session.startShell()
            Log.d(TAG, "Shell started successfully")

            val sshSession = SshSession(
                id = UUID.randomUUID().toString(),
                client = client,
                session = session,
                shell = shell,
                inputStream = shell.inputStream,
                outputStream = shell.outputStream
            )

            activeSessions[sshSession.id] = sshSession
            Log.d(TAG, "SSH session created with id: ${sshSession.id}")
            Result.success(sshSession)
        } catch (e: Exception) {
            Log.e(TAG, "SSH connection failed: ${e.message}", e)
            Result.failure(e)
        }
    }

    fun getSession(sessionId: String): SshSession? {
        return activeSessions[sessionId]
    }

    fun getAllSessions(): List<SshSession> {
        return activeSessions.values.toList()
    }

    fun closeSession(sessionId: String) {
        activeSessions[sessionId]?.close()
        activeSessions.remove(sessionId)
    }

    fun closeAllSessions() {
        activeSessions.values.forEach { it.close() }
        activeSessions.clear()
    }

    fun getActiveSessionCount(): Int {
        return activeSessions.count { it.value.isConnected() }
    }
}
