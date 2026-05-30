package com.vaultix.app.data.model

/**
 * Vault category types.
 */
enum class VaultCategory(val displayName: String, val route: String) {
    PASSWORDS("Passwords", "passwords"),
    CARDS("Cards", "cards"),
    NOTES("Notes", "notes"),
    FILES("Files", "files"),
    IDENTITIES("IDs", "identities")
}

/**
 * Decrypted password model (in-memory only, never stored as plain text).
 */
data class Password(
    val id: String,
    val title: String,
    val username: String,
    val password: CharArray,
    val website: String,
    val appPackageName: String = "",
    val notes: String,
    val passwordStrength: Int,
    val isFavorite: Boolean,
    val passwordHistory: List<CharArray> = emptyList(),
    val createdAt: Long,
    val updatedAt: Long,
    val expiresAt: Long? = null,
    val keyVersion: Int = 1
)

/**
 * Decrypted card model.
 */
data class Card(
    val id: String,
    val cardName: String,
    val holderName: String,
    val cardNumber: String,
    val expiryMonth: String,
    val expiryYear: String,
    val cvv: String,
    val cardType: String,
    val notes: String,
    val isFavorite: Boolean,
    val createdAt: Long,
    val updatedAt: Long,
    val keyVersion: Int = 1
) {
    val maskedCardNumber: String
        get() = "**** **** **** ${cardNumber.takeLast(4)}"

    val isExpired: Boolean
        get() {
            val now = java.util.Calendar.getInstance()
            val year = expiryYear.toIntOrNull() ?: return false
            val month = expiryMonth.toIntOrNull() ?: return false
            val expiryCalendar = java.util.Calendar.getInstance().apply {
                set(java.util.Calendar.YEAR, 2000 + year)
                set(java.util.Calendar.MONTH, month - 1)
                set(java.util.Calendar.DAY_OF_MONTH, getActualMaximum(java.util.Calendar.DAY_OF_MONTH))
            }
            return now.after(expiryCalendar)
        }
}

/**
 * Decrypted note model.
 */
data class Note(
    val id: String,
    val title: String,
    val content: String,
    val color: String,
    val isFavorite: Boolean,
    val isPinned: Boolean = false,
    val createdAt: Long,
    val updatedAt: Long,
    val keyVersion: Int = 1
)

/**
 * Decrypted file vault model.
 */
data class VaultFile(
    val id: String,
    val fileName: String,
    val mimeType: String,
    val encryptedFilePath: String,
    val fileSizeBytes: Long,
    val notes: String,
    val folderId: String? = null,
    val isFavorite: Boolean,
    val createdAt: Long,
    val updatedAt: Long,
    val keyVersion: Int = 1
)

/**
 * Decrypted folder model.
 */
data class VaultFolder(
    val id: String,
    val name: String,
    val parentFolderId: String? = null,
    val createdAt: Long,
    val updatedAt: Long
)

/**
 * Decrypted identity model.
 */
data class Identity(
    val id: String,
    val documentType: String,
    val documentName: String,
    val documentNumber: String,
    val fullName: String,
    val dateOfBirth: String,
    val issuedBy: String,
    val issuedDate: String,
    val expiryDate: String,
    val nationality: String,
    val notes: String,
    val imagePaths: List<String> = emptyList(),
    val isFavorite: Boolean,
    val createdAt: Long,
    val updatedAt: Long,
    val keyVersion: Int = 1
)

/**
 * Model for full vault backup.
 */
data class BackupData(
    val passwords: List<Password>,
    val cards: List<Card>,
    val notes: List<Note>,
    val identities: List<Identity>,
    val version: Int = 1,
    val timestamp: Long = System.currentTimeMillis()
)

/**
 * Password strength levels.
 */
enum class PasswordStrength(val level: Int, val label: String) {
    VERY_WEAK(0, "Very Weak"),
    WEAK(1, "Weak"),
    FAIR(2, "Fair"),
    STRONG(3, "Strong"),
    VERY_STRONG(4, "Very Strong")
}

/**
 * Authentication state.
 */
sealed class AuthState {
    object Unauthenticated : AuthState()
    object Authenticated : AuthState()
    object Loading : AuthState()
    data class Error(val message: String) : AuthState()
}
