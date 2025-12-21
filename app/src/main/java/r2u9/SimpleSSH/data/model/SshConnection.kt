package r2u9.SimpleSSH.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "ssh_connections")
data class SshConnection(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    val host: String,
    val port: Int = 22,
    val username: String,
    val authType: AuthType = AuthType.PASSWORD,
    val password: String? = null,
    val privateKey: String? = null,
    val privateKeyPassphrase: String? = null,
    val colorTheme: String = "Default",
    val createdAt: Long = System.currentTimeMillis(),
    val lastConnectedAt: Long? = null,
    val wolEnabled: Boolean = false,
    val wolMacAddress: String? = null,
    val wolBroadcastAddress: String? = null,
    val wolPort: Int = 9
)

enum class AuthType {
    PASSWORD,
    KEY
}
