package r2u9.SimpleSSH.util

import android.content.Context
import android.net.Uri
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.reflect.TypeToken
import r2u9.SimpleSSH.data.model.AuthType
import r2u9.SimpleSSH.data.model.SshConnection
import java.io.BufferedReader
import java.io.InputStreamReader

data class ExportableConnection(
    val name: String,
    val host: String,
    val port: Int,
    val username: String,
    val authType: String,
    val password: String?,
    val privateKey: String?,
    val privateKeyPassphrase: String?,
    val colorTheme: String
)

object ConfigExporter {
    private val gson: Gson = GsonBuilder().setPrettyPrinting().create()

    fun exportConnections(connections: List<SshConnection>): String {
        val exportable = connections.map { conn ->
            ExportableConnection(
                name = conn.name,
                host = conn.host,
                port = conn.port,
                username = conn.username,
                authType = conn.authType.name,
                password = conn.password,
                privateKey = conn.privateKey,
                privateKeyPassphrase = conn.privateKeyPassphrase,
                colorTheme = conn.colorTheme
            )
        }
        return gson.toJson(exportable)
    }

    fun importConnections(json: String): List<SshConnection> {
        val type = object : TypeToken<List<ExportableConnection>>() {}.type
        val exportable: List<ExportableConnection> = gson.fromJson(json, type)
        return exportable.map { exp ->
            SshConnection(
                name = exp.name,
                host = exp.host,
                port = exp.port,
                username = exp.username,
                authType = AuthType.valueOf(exp.authType),
                password = exp.password,
                privateKey = exp.privateKey,
                privateKeyPassphrase = exp.privateKeyPassphrase,
                colorTheme = exp.colorTheme
            )
        }
    }

    fun readFromUri(context: Context, uri: Uri): String {
        return context.contentResolver.openInputStream(uri)?.use { inputStream ->
            BufferedReader(InputStreamReader(inputStream)).use { reader ->
                reader.readText()
            }
        } ?: throw IllegalStateException("Could not open input stream")
    }

    fun writeToUri(context: Context, uri: Uri, content: String) {
        context.contentResolver.openOutputStream(uri)?.use { outputStream ->
            outputStream.write(content.toByteArray())
        } ?: throw IllegalStateException("Could not open output stream")
    }
}
