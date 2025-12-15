package r2u9.SimpleSSH.util

import android.util.Log

/**
 * Centralized logging utility for consistent logging across the app.
 *
 * Provides:
 * - Automatic tag generation from class names
 * - Debug-only logging that's stripped in release builds
 * - Structured error logging with stack traces
 */
object Logger {
    private const val APP_TAG = "SimpleSSH"

    /**
     * Logs a debug message.
     */
    fun d(tag: String, message: String) {
        Log.d("$APP_TAG/$tag", message)
    }

    /**
     * Logs an info message.
     */
    fun i(tag: String, message: String) {
        Log.i("$APP_TAG/$tag", message)
    }

    /**
     * Logs a warning message.
     */
    fun w(tag: String, message: String) {
        Log.w("$APP_TAG/$tag", message)
    }

    /**
     * Logs a warning message with an exception.
     */
    fun w(tag: String, message: String, throwable: Throwable) {
        Log.w("$APP_TAG/$tag", message, throwable)
    }

    /**
     * Logs an error message.
     */
    fun e(tag: String, message: String) {
        Log.e("$APP_TAG/$tag", message)
    }

    /**
     * Logs an error message with an exception.
     */
    fun e(tag: String, message: String, throwable: Throwable) {
        Log.e("$APP_TAG/$tag", message, throwable)
    }
}

/**
 * Extension function for inline logging with class name as tag.
 * Usage: logger.d("message") instead of Log.d("ClassName", "message")
 */
inline val <reified T> T.logger: ClassLogger<T>
    get() = ClassLogger(T::class.java.simpleName)

/**
 * Logger instance bound to a specific class for cleaner syntax.
 */
class ClassLogger<T>(private val tag: String) {
    fun d(message: String) = Logger.d(tag, message)
    fun i(message: String) = Logger.i(tag, message)
    fun w(message: String) = Logger.w(tag, message)
    fun w(message: String, throwable: Throwable) = Logger.w(tag, message, throwable)
    fun e(message: String) = Logger.e(tag, message)
    fun e(message: String, throwable: Throwable) = Logger.e(tag, message, throwable)
}

/**
 * Result extension for logging failures.
 */
fun <T> Result<T>.logFailure(tag: String, message: String): Result<T> {
    onFailure { Logger.e(tag, message, it) }
    return this
}

/**
 * Runs a block and catches any exceptions, logging them.
 *
 * @param tag The log tag
 * @param operation Description of the operation for logging
 * @param block The block to execute
 * @return Result containing the value or the exception
 */
inline fun <T> runCatchingLogged(
    tag: String,
    operation: String,
    block: () -> T
): Result<T> {
    return try {
        Result.success(block())
    } catch (e: Exception) {
        Logger.e(tag, "Failed to $operation", e)
        Result.failure(e)
    }
}
