package com.vaultix.app.ui.viewmodel

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject

data class GeneratedPasswordEntry(
    val password: String,
    val timestamp: String
)

@HiltViewModel
class PasswordGeneratorViewModel @Inject constructor() : ViewModel() {

    private val _generatedPassword = MutableStateFlow("")
    val generatedPassword: StateFlow<String> = _generatedPassword.asStateFlow()

    private val _passwordHistory = MutableStateFlow<List<GeneratedPasswordEntry>>(emptyList())
    val passwordHistory: StateFlow<List<GeneratedPasswordEntry>> = _passwordHistory.asStateFlow()

    fun generate(
        length: Int = 16,
        includeUppercase: Boolean = true,
        includeNumbers: Boolean = true,
        includeSymbols: Boolean = true
    ) {
        val lowercase = "abcdefghijklmnopqrstuvwxyz"
        val uppercase = "ABCDEFGHIJKLMNOPQRSTUVWXYZ"
        val numbers = "0123456789"
        val symbols = "!@#$%^&*()_+-=[]{}|;:,.<>?"

        var charPool = lowercase
        if (includeUppercase) charPool += uppercase
        if (includeNumbers) charPool += numbers
        if (includeSymbols) charPool += symbols

        val password = (1..length)
            .map { charPool.random() }
            .joinToString("")
        
        _generatedPassword.value = password
        
        // Add to history (keep only last 3)
        val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())
        val newEntry = GeneratedPasswordEntry(password, timestamp)
        val currentHistory = _passwordHistory.value.toMutableList()
        currentHistory.add(0, newEntry)
        if (currentHistory.size > 3) {
            currentHistory.removeAt(currentHistory.size - 1)
        }
        _passwordHistory.value = currentHistory
    }
}
