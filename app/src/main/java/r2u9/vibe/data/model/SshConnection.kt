package r2u9.vibe.data.model

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
    val colorTheme: String = "default",
    val createdAt: Long = System.currentTimeMillis(),
    val lastConnectedAt: Long? = null
)

enum class AuthType {
    PASSWORD,
    KEY
}
