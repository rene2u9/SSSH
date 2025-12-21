package r2u9.SimpleSSH.util

import android.content.Context
import android.content.Intent
import android.content.pm.ShortcutInfo
import android.content.pm.ShortcutManager
import android.graphics.drawable.Icon
import android.os.Build
import r2u9.SimpleSSH.MainActivity
import r2u9.SimpleSSH.R
import r2u9.SimpleSSH.data.model.SshConnection

object ShortcutHelper {

    const val ACTION_CONNECT = "r2u9.SimpleSSH.ACTION_CONNECT"
    const val EXTRA_CONNECTION_ID = "connection_id"
    private const val MAX_SHORTCUTS = 4

    fun updateShortcuts(context: Context, connections: List<SshConnection>) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N_MR1) return

        val shortcutManager = context.getSystemService(ShortcutManager::class.java) ?: return

        val shortcuts = connections
            .sortedByDescending { it.lastConnectedAt ?: 0 }
            .take(MAX_SHORTCUTS)
            .map { connection ->
                ShortcutInfo.Builder(context, "connection_${connection.id}")
                    .setShortLabel(connection.name)
                    .setLongLabel("Connect to ${connection.name}")
                    .setIcon(Icon.createWithResource(context, R.drawable.ic_terminal))
                    .setIntent(
                        Intent(context, MainActivity::class.java).apply {
                            action = ACTION_CONNECT
                            putExtra(EXTRA_CONNECTION_ID, connection.id)
                            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                        }
                    )
                    .build()
            }

        shortcutManager.dynamicShortcuts = shortcuts
    }

    fun removeShortcut(context: Context, connectionId: Long) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N_MR1) return

        val shortcutManager = context.getSystemService(ShortcutManager::class.java) ?: return
        shortcutManager.removeDynamicShortcuts(listOf("connection_$connectionId"))
    }
}
