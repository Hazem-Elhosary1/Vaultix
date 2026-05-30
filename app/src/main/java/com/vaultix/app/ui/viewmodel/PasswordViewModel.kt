package com.vaultix.app.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vaultix.app.data.model.Password
import com.vaultix.app.data.model.PasswordStrength
import com.vaultix.app.data.repository.PasswordRepository
import com.vaultix.app.debug.DebugCategory
import com.vaultix.app.debug.DebugEventBus
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.security.SecureRandom
import java.util.UUID
import javax.inject.Inject

data class PasswordUiState(
    val passwords: List<Password> = emptyList(),
    val searchQuery: String = "",
    val isLoading: Boolean = false,
    val error: String? = null,
    val generatedPassword: String = ""
)

@HiltViewModel
class PasswordViewModel @Inject constructor(
    private val passwordRepository: PasswordRepository
) : ViewModel() {

    private val _searchQuery = MutableStateFlow("")
    private val _uiState = MutableStateFlow(PasswordUiState(isLoading = true))
    val uiState: StateFlow<PasswordUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            combine(
                passwordRepository.getAllPasswords(),
                _searchQuery
            ) { passwords, query ->
                val filtered = if (query.isBlank()) passwords
                else passwords.filter { p ->
                    p.title.contains(query, ignoreCase = true) ||
                    p.username.contains(query, ignoreCase = true) ||
                    p.website.contains(query, ignoreCase = true) ||
                    p.appPackageName.contains(query, ignoreCase = true)
                }
                PasswordUiState(
                    passwords = filtered,
                    searchQuery = query,
                    isLoading = false
                )
            }.collect { state ->
                _uiState.value = state
            }
        }
    }

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun insertPassword(password: Password) {
        viewModelScope.launch {
            try {
                val now = System.currentTimeMillis()
                passwordRepository.insertPassword(
                    password.copy(
                        id = UUID.randomUUID().toString(),
                        createdAt = now,
                        updatedAt = now,
                        passwordStrength = calculateStrength(password.password).level
                    )
                )
                DebugEventBus.log(
                    category  = DebugCategory.CRUD,
                    eventType = "PASSWORD_CREATED",
                    details   = "title=${password.title}, site=${password.website.ifBlank { "n/a" }}",
                    source    = "PasswordViewModel"
                )
            } catch (e: Exception) {
                _uiState.update { it.copy(error = e.message) }
                DebugEventBus.log(
                    category  = DebugCategory.CRUD,
                    eventType = "PASSWORD_CREATE_FAILED",
                    details   = e.message ?: "unknown error",
                    severity  = com.vaultix.app.debug.DebugSeverity.WARNING,
                    source    = "PasswordViewModel"
                )
            }
        }
    }

    fun updatePassword(password: Password) {
        viewModelScope.launch {
            try {
                passwordRepository.updatePassword(
                    password.copy(
                        updatedAt = System.currentTimeMillis(),
                        passwordStrength = calculateStrength(password.password).level
                    )
                )
                DebugEventBus.log(
                    category  = DebugCategory.CRUD,
                    eventType = "PASSWORD_UPDATED",
                    details   = "id=${password.id}, title=${password.title}",
                    source    = "PasswordViewModel"
                )
            } catch (e: Exception) {
                _uiState.update { it.copy(error = e.message) }
            }
        }
    }

    fun deletePassword(id: String) {
        viewModelScope.launch {
            passwordRepository.deletePassword(id)
            DebugEventBus.log(
                category  = DebugCategory.CRUD,
                eventType = "PASSWORD_DELETED",
                details   = "id=$id",
                severity  = com.vaultix.app.debug.DebugSeverity.WARNING,
                source    = "PasswordViewModel"
            )
        }
    }

    fun toggleFavorite(password: Password) {
        viewModelScope.launch {
            passwordRepository.updatePassword(password.copy(isFavorite = !password.isFavorite))
            DebugEventBus.log(
                category  = DebugCategory.CRUD,
                eventType = "PASSWORD_FAVORITE_TOGGLED",
                details   = "id=${password.id}, isFavorite=${!password.isFavorite}",
                source    = "PasswordViewModel"
            )
        }
    }

    fun generatePassword(
        length: Int = 20,
        useUppercase: Boolean = true,
        useLowercase: Boolean = true,
        useNumbers: Boolean = true,
        useSymbols: Boolean = true
    ): String {
        val chars = buildString {
            if (useUppercase) append("ABCDEFGHIJKLMNOPQRSTUVWXYZ")
            if (useLowercase) append("abcdefghijklmnopqrstuvwxyz")
            if (useNumbers) append("0123456789")
            if (useSymbols) append("!@#\$%^&*()_+-=[]{}|;:,.<>?")
        }

        if (chars.isEmpty()) return ""

        val random = SecureRandom()
        val generated = (1..length).map { chars[random.nextInt(chars.length)] }.joinToString("")
        _uiState.update { it.copy(generatedPassword = generated) }
        return generated
    }

    fun calculateStrength(password: CharArray): PasswordStrength {
        if (password.isEmpty()) return PasswordStrength.VERY_WEAK

        var score = 0

        // Length scoring
        when {
            password.size >= 20 -> score += 2
            password.size >= 12 -> score += 1
        }

        // Character variety
        if (password.any { it.isUpperCase() }) score++
        if (password.any { it.isLowerCase() }) score++
        if (password.any { it.isDigit() }) score++
        if (password.any { !it.isLetterOrDigit() }) score++

        return when (score) {
            0, 1 -> PasswordStrength.VERY_WEAK
            2 -> PasswordStrength.WEAK
            3 -> PasswordStrength.FAIR
            4, 5 -> PasswordStrength.STRONG
            else -> PasswordStrength.VERY_STRONG
        }
    }

    /**
     * Priority 3: Duplicate Detection
     * Checks if the exact password already exists in another account in the current vault.
     */
    fun isPasswordDuplicate(password: CharArray, excludeId: String? = null): Boolean {
        if (password.isEmpty()) return false
        val currentPasswords = uiState.value.passwords
        return currentPasswords.any { it.id != excludeId && it.password.contentEquals(password) }
    }

    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }
}
