package com.vaultix.app.service

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.service.autofill.Dataset
import android.service.autofill.FillResponse
import android.view.autofill.AutofillManager
import android.view.autofill.AutofillValue
import android.widget.RemoteViews
import androidx.appcompat.app.AppCompatActivity
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import com.vaultix.app.data.local.dao.PasswordDao
import com.vaultix.app.security.CryptoManager
import com.vaultix.app.security.SecurePreferences
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.crypto.SecretKey
import javax.crypto.spec.SecretKeySpec

@AndroidEntryPoint
class AutofillAuthActivity : AppCompatActivity() {

    @Inject lateinit var passwordRepository: com.vaultix.app.data.repository.PasswordRepository
    @Inject lateinit var cryptoManager: CryptoManager
    @Inject lateinit var securePreferences: SecurePreferences

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        android.util.Log.d("VaultixAuth", "Activity onCreate started")
        
        val passwordId = intent.getStringExtra("password_id")
        val usernameAutofillId = intent.getParcelableExtra<android.view.autofill.AutofillId>("username_id")
        val passwordAutofillId = intent.getParcelableExtra<android.view.autofill.AutofillId>("password_id_id")

        android.util.Log.d("VaultixAuth", "Received IDs - pwd: $passwordId, userAF: $usernameAutofillId, pwdAF: $passwordAutofillId")

        if (passwordId == null) {
            android.util.Log.e("VaultixAuth", "passwordId is null, finishing")
            finish()
            return
        }

        showBiometricPrompt {
            android.util.Log.d("VaultixAuth", "Biometric success, starting decryption")
            decryptAndReturn(passwordId, usernameAutofillId, passwordAutofillId)
        }
    }

    private fun showBiometricPrompt(onSuccess: () -> Unit) {
        val executor = ContextCompat.getMainExecutor(this)
        val biometricPrompt = BiometricPrompt(this, executor,
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    onSuccess()
                }
                override fun onAuthenticationFailed() {
                    // Fallback or error handled by system
                }
            })

        val promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle("Vaultix Authentication")
            .setSubtitle("Confirm identity to autofill")
            .setNegativeButtonText("Cancel")
            .build()

        biometricPrompt.authenticate(promptInfo)
    }

    private fun decryptAndReturn(passwordId: String, usernameId: android.view.autofill.AutofillId?, passwordIdId: android.view.autofill.AutofillId?) {
        lifecycleScope.launch {
            try {
                android.util.Log.d("VaultixAuth", "Fetching password from repository for ID: $passwordId")
                val password = passwordRepository.getPasswordById(passwordId) ?: run {
                    android.util.Log.e("VaultixAuth", "Password not found in DB")
                    setResult(Activity.RESULT_CANCELED)
                    finish()
                    return@launch
                }
                
                android.util.Log.d("VaultixAuth", "Password fetched successfully. Building dataset...")
                val datasetBuilder = Dataset.Builder()
                var hasValues = false

                // Add presentation to the result dataset as well (required by some Android versions)
                val usernameText = password.username
                val passwordText = String(password.password)
                
                val presentation = RemoteViews(packageName, com.vaultix.app.R.layout.autofill_suggestion_item).apply {
                    setTextViewText(com.vaultix.app.R.id.text_title, "${password.title} ($usernameText)")
                }

                usernameId?.let {
                    android.util.Log.d("VaultixAuth", "Setting decrypted username field")
                    datasetBuilder.setValue(it, AutofillValue.forText(usernameText), presentation)
                    hasValues = true
                }
                passwordIdId?.let {
                    android.util.Log.d("VaultixAuth", "Setting decrypted password field")
                    datasetBuilder.setValue(it, AutofillValue.forText(passwordText), presentation)
                    hasValues = true
                }

                if (hasValues) {
                    android.util.Log.d("VaultixAuth", "Returning dataset to Android System")
                    val replyIntent = Intent()
                    replyIntent.putExtra(AutofillManager.EXTRA_AUTHENTICATION_RESULT, datasetBuilder.build())
                    setResult(Activity.RESULT_OK, replyIntent)
                } else {
                    android.util.Log.w("VaultixAuth", "No values set in dataset, canceling")
                    setResult(Activity.RESULT_CANCELED)
                }
                finish()
            } catch (e: Exception) {
                android.util.Log.e("VaultixAuth", "Error in decryptAndReturn: ${e.message}", e)
                setResult(Activity.RESULT_CANCELED)
                finish()
            }
        }
    }
}
