package com.vaultix.app.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vaultix.app.data.repository.CardRepository
import com.vaultix.app.data.repository.IdentityRepository
import com.vaultix.app.data.repository.PasswordRepository
import com.vaultix.app.security.PasswordHealthAnalyzer
import com.vaultix.app.security.PasswordHealthReport
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*
import javax.inject.Inject

@HiltViewModel
class SecurityAuditViewModel @Inject constructor(
    private val passwordRepository: PasswordRepository,
    private val cardRepository: CardRepository,
    private val identityRepository: IdentityRepository,
    private val securityRepository: com.vaultix.app.data.repository.SecurityRepository,
    private val healthAnalyzer: PasswordHealthAnalyzer
) : ViewModel() {

    private val _uiState = MutableStateFlow(SecurityAuditUiState())
    val uiState: StateFlow<SecurityAuditUiState> = _uiState.asStateFlow()

    private val _logs = MutableStateFlow<List<com.vaultix.app.data.repository.SecurityLog>>(emptyList())
    val logs: StateFlow<List<com.vaultix.app.data.repository.SecurityLog>> = _logs.asStateFlow()

    init {
        loadAuditData()
        loadLogs()
    }

    private fun loadLogs() {
        viewModelScope.launch {
            securityRepository.getRecentLogs().collect {
                _logs.value = it
            }
        }
    }

    private fun loadAuditData() {
        viewModelScope.launch {
            combine(
                passwordRepository.getAllPasswords(),
                cardRepository.getAllCards(),
                identityRepository.getAllIdentities()
            ) { passwords, cards, identities ->
                // Separate standard passwords from Wi-Fi entries
                val standardPasswords = passwords.filter { it.website != "vaultix://wifi" }
                val wifiEntries = passwords.filter { it.website == "vaultix://wifi" }

                // Analyze only standard passwords for health report
                val passwordReport = healthAnalyzer.analyze(standardPasswords)
                
                // Analyze Wi-Fi security
                val weakWifiItems = wifiEntries.filter { wifi ->
                    wifi.appPackageName == "Open" ||
                    wifi.appPackageName == "WEP" ||
                    wifi.passwordStrength < 3
                }

                val expiringCards = cards.filter { isCardExpiringSoon(it.expiryMonth, it.expiryYear) }
                val expiringIdentities = identities.filter { isIdentityExpiringSoon(it.expiryDate) }

                SecurityAuditUiState(
                    passwordReport = passwordReport,
                    weakWifiCount = weakWifiItems.size,
                    weakWifiItems = weakWifiItems,
                    expiringCardsCount = expiringCards.size,
                    expiringIdentitiesCount = expiringIdentities.size,
                    expiringCards = expiringCards.map { "${it.holderName} (${it.cardNumber.takeLast(4)})" },
                    expiringIdentities = expiringIdentities.map { "${it.fullName} (${it.documentType})" }
                )
            }.collect {
                _uiState.value = it
            }
        }
    }

    private fun isCardExpiringSoon(month: String, year: String): Boolean {
        return try {
            val m = month.toIntOrNull() ?: return false
            val y = year.toIntOrNull() ?: return false
            val cal = Calendar.getInstance()
            val expiry = Calendar.getInstance().apply {
                set(Calendar.YEAR, 2000 + y)
                set(Calendar.MONTH, m - 1)
                set(Calendar.DAY_OF_MONTH, 1)
            }
            val threshold = Calendar.getInstance().apply { add(Calendar.MONTH, 1) }
            expiry.before(threshold)
        } catch (e: Exception) {
            false
        }
    }

    private fun isIdentityExpiringSoon(expiryDate: String): Boolean {
        if (expiryDate.isEmpty()) return false
        return try {
            // Adjust format based on how you store identity expiry dates
            val sdf = SimpleDateFormat("dd/MM/yyyy", Locale.US) 
            val expiry = sdf.parse(expiryDate) ?: return false
            val threshold = Calendar.getInstance().apply { add(Calendar.MONTH, 1) }
            expiry.before(threshold.time)
        } catch (e: Exception) {
            false
        }
    }

    fun clearLogs() {
        viewModelScope.launch {
            securityRepository.clearLogs()
        }
    }
}

data class SecurityAuditUiState(
    val passwordReport: PasswordHealthReport = PasswordHealthReport(),
    val weakWifiCount: Int = 0,
    val weakWifiItems: List<com.vaultix.app.data.model.Password> = emptyList(),
    val expiringCardsCount: Int = 0,
    val expiringIdentitiesCount: Int = 0,
    val expiringCards: List<String> = emptyList(),
    val expiringIdentities: List<String> = emptyList(),
    val isLoading: Boolean = false
)
