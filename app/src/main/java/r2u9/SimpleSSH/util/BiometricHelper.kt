package r2u9.SimpleSSH.util

import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity

/**
 * Helper object for biometric authentication (fingerprint, face recognition).
 *
 * Provides methods to check biometric availability and perform authentication
 * for protecting sensitive actions like connecting to servers or revealing passwords.
 */
object BiometricHelper {

    /**
     * Checks if biometric authentication is available and configured on the device.
     *
     * @param activity The activity context
     * @return true if biometric authentication can be used, false otherwise
     */
    fun canAuthenticate(activity: FragmentActivity): Boolean {
        val biometricManager = BiometricManager.from(activity)
        return biometricManager.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG or BiometricManager.Authenticators.BIOMETRIC_WEAK) == BiometricManager.BIOMETRIC_SUCCESS
    }

    /**
     * Shows the biometric authentication prompt.
     *
     * @param activity The activity to show the prompt in
     * @param title The title displayed in the biometric prompt
     * @param subtitle The subtitle displayed in the biometric prompt
     * @param onSuccess Called when authentication succeeds
     * @param onError Called when an error occurs (except user cancellation)
     * @param onFailed Called when authentication fails (wrong biometric)
     */
    fun authenticate(
        activity: FragmentActivity,
        title: String,
        subtitle: String,
        onSuccess: () -> Unit,
        onError: (String) -> Unit,
        onFailed: () -> Unit = {}
    ) {
        val executor = ContextCompat.getMainExecutor(activity)

        val callback = object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                super.onAuthenticationSucceeded(result)
                onSuccess()
            }

            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                super.onAuthenticationError(errorCode, errString)
                if (errorCode != BiometricPrompt.ERROR_USER_CANCELED &&
                    errorCode != BiometricPrompt.ERROR_NEGATIVE_BUTTON) {
                    onError(errString.toString())
                }
            }

            override fun onAuthenticationFailed() {
                super.onAuthenticationFailed()
                onFailed()
            }
        }

        val biometricPrompt = BiometricPrompt(activity, executor, callback)

        val promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle(title)
            .setSubtitle(subtitle)
            .setNegativeButtonText("Cancel")
            .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_STRONG or BiometricManager.Authenticators.BIOMETRIC_WEAK)
            .build()

        biometricPrompt.authenticate(promptInfo)
    }

    /**
     * Authenticates only if biometric is enabled in preferences.
     * If biometric is disabled or unavailable, calls onSuccess immediately.
     *
     * @param activity The activity to show the prompt in
     * @param prefs The app preferences to check biometric setting
     * @param title The title displayed in the biometric prompt
     * @param subtitle The subtitle displayed in the biometric prompt
     * @param onSuccess Called when authentication succeeds or biometric is disabled
     * @param onError Called when an error occurs
     */
    fun authenticateIfEnabled(
        activity: FragmentActivity,
        prefs: AppPreferences,
        title: String,
        subtitle: String,
        onSuccess: () -> Unit,
        onError: (String) -> Unit = {}
    ) {
        if (prefs.biometricEnabled && canAuthenticate(activity)) {
            authenticate(activity, title, subtitle, onSuccess, onError)
        } else {
            onSuccess()
        }
    }
}
