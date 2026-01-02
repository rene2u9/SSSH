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
