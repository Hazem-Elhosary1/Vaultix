package com.vaultix.app.security

import android.app.Activity
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import kotlinx.coroutines.suspendCancellableCoroutine
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume

@Singleton
class BiometricManager @Inject constructor() {

    suspend fun verifyBiometric(activity: Activity, title: String = "Verify Identity"): Boolean {
        val fragmentActivity = activity as? FragmentActivity
            ?: return false

        return suspendCancellableCoroutine { continuation ->
            val biometricManager = androidx.biometric.BiometricManager.from(fragmentActivity)
            val canAuthenticate = biometricManager.canAuthenticate(androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG)

            when (canAuthenticate) {
                androidx.biometric.BiometricManager.BIOMETRIC_SUCCESS -> {
                    val executor = ContextCompat.getMainExecutor(fragmentActivity)
                    val callback = object : BiometricPrompt.AuthenticationCallback() {
                        override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                            super.onAuthenticationSucceeded(result)
                            if (continuation.isActive) {
                                continuation.resume(true)
                            }
                        }

                        override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                            super.onAuthenticationError(errorCode, errString)
                            if (continuation.isActive) {
                                continuation.resume(false)
                            }
                        }

                        override fun onAuthenticationFailed() {
                            super.onAuthenticationFailed()
                            // Don't resume here — user can retry scanning
                        }
                    }

                    val promptInfo = BiometricPrompt.PromptInfo.Builder()
                        .setTitle(title)
                        .setSubtitle("Authentication is required to proceed")
                        .setNegativeButtonText("Cancel")
                        .setAllowedAuthenticators(androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG)
                        .build()

                    val biometricPrompt = BiometricPrompt(fragmentActivity, executor, callback)
                    biometricPrompt.authenticate(promptInfo)
                }
                else -> {
                    if (continuation.isActive) {
                        continuation.resume(false)
                    }
                }
            }
        }
    }
}
