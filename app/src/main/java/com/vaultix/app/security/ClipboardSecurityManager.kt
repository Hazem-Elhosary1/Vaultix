package com.vaultix.app.security

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Manages secure clipboard operations:
 * - Copies sensitive data to clipboard
 * - Auto-clears after a configurable timeout
 * - Notifies user of copy and upcoming clear
 */
@Singleton
class ClipboardSecurityManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val securePreferences: SecurePreferences
) {
    private val handler = Handler(Looper.getMainLooper())
    private var clearRunnable: Runnable? = null

    companion object {
        const val DEFAULT_CLEAR_DELAY_SECONDS = 30
    }

    /**
     * Copies text to clipboard and schedules auto-clear.
     * @param label Description of what was copied (e.g., "Password", "Card Number")
     * @param text The sensitive text to copy
     * @param showToast Whether to show a toast notification
     */
    fun copyAndScheduleClear(label: String, text: String, showToast: Boolean = true) {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager

        // Copy to clipboard
        val clip = ClipData.newPlainText(label, text)
        clipboard.setPrimaryClip(clip)

        // Cancel any previous scheduled clear
        cancelPendingClear()

        // Get configured delay
        val delaySeconds = getConfiguredDelay()

        if (delaySeconds > 0) {
            // Schedule clear
            clearRunnable = Runnable {
                clearClipboard()
            }
            handler.postDelayed(clearRunnable!!, delaySeconds * 1000L)

            if (showToast) {
                Toast.makeText(
                    context,
                    "$label copied! Auto-clear in ${delaySeconds}s 🔒",
                    Toast.LENGTH_SHORT
                ).show()
            }
        } else {
            if (showToast) {
                Toast.makeText(context, "$label copied!", Toast.LENGTH_SHORT).show()
            }
        }
    }

    /**
     * Immediately clears the clipboard.
     */
    fun clearClipboard() {
        try {
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            clipboard.setPrimaryClip(ClipData.newPlainText("", ""))
        } catch (e: Exception) {
            // Silently ignore
        }
    }

    /**
     * Cancels any pending clipboard clear operation.
     */
    fun cancelPendingClear() {
        clearRunnable?.let { handler.removeCallbacks(it) }
        clearRunnable = null
    }

    /**
     * Returns the configured clipboard clear delay in seconds.
     * Returns 0 if auto-clear is disabled.
     */
    private fun getConfiguredDelay(): Int {
        return try {
            kotlinx.coroutines.runBlocking {
                securePreferences.getInt(
                    SecurePreferences.KEY_CLIPBOARD_CLEAR_DELAY,
                    DEFAULT_CLEAR_DELAY_SECONDS
                )
            }
        } catch (e: Exception) {
            DEFAULT_CLEAR_DELAY_SECONDS
        }
    }

    @dagger.hilt.EntryPoint
    @dagger.hilt.InstallIn(dagger.hilt.components.SingletonComponent::class)
    interface ClipboardSecurityEntryPoint {
        fun clipboardSecurityManager(): ClipboardSecurityManager
    }
}
