package r2u9.SimpleSSH.util

import android.content.Context
import android.view.View
import android.widget.Toast
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.InetSocketAddress
import java.net.Socket

/**
 * Extension functions for common operations throughout the app.
 */

// View extensions

/** Shows the view by setting visibility to VISIBLE. */
fun View.show() {
    visibility = View.VISIBLE
}

/** Hides the view by setting visibility to GONE. */
fun View.hide() {
    visibility = View.GONE
}

/** Sets visibility to INVISIBLE (hidden but still takes up space). */
fun View.invisible() {
    visibility = View.INVISIBLE
}

/** Sets visibility based on a boolean condition. */
fun View.visibleIf(condition: Boolean) {
    visibility = if (condition) View.VISIBLE else View.GONE
}

/** Shows a Snackbar with the given message. */
fun View.showSnackbar(message: String, duration: Int = Snackbar.LENGTH_SHORT) {
    Snackbar.make(this, message, duration).show()
}

/** Shows an error Snackbar with red background. */
fun View.showErrorSnackbar(message: String) {
    Snackbar.make(this, message, Snackbar.LENGTH_LONG).apply {
        setBackgroundTint(context.getColor(android.R.color.holo_red_dark))
    }.show()
}

// Context extensions

/** Shows a short Toast message. */
fun Context.toast(message: String) {
    Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
}

/** Shows a long Toast message. */
fun Context.toastLong(message: String) {
    Toast.makeText(this, message, Toast.LENGTH_LONG).show()
}

// String extensions

/** Returns true if the string is not null or blank. */
fun String?.isNotNullOrBlank(): Boolean = !isNullOrBlank()

/** Returns the string or a default value if null or blank. */
fun String?.orDefault(default: String): String = if (isNullOrBlank()) default else this

// Network extensions

/**
 * Checks if a host is reachable on the specified port.
 *
 * @param host The hostname or IP address
 * @param port The port to check
 * @param timeoutMs Connection timeout in milliseconds
 * @return true if the host is reachable, false otherwise
 */
suspend fun isHostReachable(
    host: String,
    port: Int,
    timeoutMs: Int = Constants.Time.HOST_REACHABILITY_TIMEOUT_MS
): Boolean = withContext(Dispatchers.IO) {
    try {
        Socket().use { socket ->
            socket.connect(InetSocketAddress(host, port), timeoutMs)
            true
        }
    } catch (e: Exception) {
        false
    }
}

// Duration formatting

/**
 * Formats milliseconds as HH:MM:SS duration string.
 */
fun Long.formatAsDuration(): String {
    val seconds = (this / Constants.Time.MS_PER_SECOND) % 60
    val minutes = (this / Constants.Time.MS_PER_MINUTE) % 60
    val hours = this / Constants.Time.MS_PER_HOUR
    return "%02d:%02d:%02d".format(hours, minutes, seconds)
}

// Coroutine extensions

/**
 * Launches a coroutine on the IO dispatcher from a LifecycleOwner.
 */
fun LifecycleOwner.launchIO(block: suspend CoroutineScope.() -> Unit) {
    lifecycleScope.launch(Dispatchers.IO, block = block)
}

/**
 * Launches a coroutine on the Main dispatcher from a LifecycleOwner.
 */
fun LifecycleOwner.launchMain(block: suspend CoroutineScope.() -> Unit) {
    lifecycleScope.launch(Dispatchers.Main, block = block)
}
