package com.vaultix.app.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vaultix.app.security.SecurePreferences
import com.vaultix.app.ui.theme.FontSizeScale
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject
import java.util.Locale

enum class ThemeMode { SYSTEM, LIGHT, DARK }

data class PremiumPlan(
    val id: String,
    val name: String,
    val price: String,
    val period: String, // Monthly, Yearly, Lifetime
    val description: String = ""
)

data class AppConfigState(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val accentColorHex: String = "#FF9800", // Default VaultOrange
    val language: String = "en",
    val fontSizeScale: FontSizeScale = FontSizeScale.MEDIUM,
    val isPremium: Boolean = true,
    val supportEmail: String = "hazemelhosary3@gmail.com",
    val supportPhone: String = "+201234567890",
    val isDeveloperMode: Boolean = false,
    val notifCardExpiry: Boolean = true,
    val notifWeakPasswords: Boolean = true,
    val notifAutoBackup: Boolean = true,
    val notifExpiryThreshold: Int = 30,
    val availablePlans: List<PremiumPlan> = listOf(
        PremiumPlan("monthly", "Monthly", "$4.99", "per month"),
        PremiumPlan("yearly", "Yearly", "$39.99", "per year", "Best Value!"),
        PremiumPlan("lifetime", "Lifetime", "$99.99", "one time")
    )
)

@HiltViewModel
class AppConfigViewModel @Inject constructor(
    private val securePreferences: SecurePreferences
) : ViewModel() {

    private val _configState = MutableStateFlow(AppConfigState())
    val configState: StateFlow<AppConfigState> = _configState.asStateFlow()

    init {
        var initialTheme: String? = null
        var initialColor: String? = null
        var initialLang: String? = null
        var initialPremium = true
        var initialFontScale: String? = null
        var initialSupportEmail: String? = null
        var initialSupportPhone: String? = null
        var initialDevMode = false
        var initialNotifCardExpiry = true
        var initialNotifWeakPasswords = true
        var initialNotifAutoBackup = true
        var initialNotifExpiryThreshold = 30

        try {
            kotlinx.coroutines.runBlocking {
                initialTheme = securePreferences.getPlainString(SecurePreferences.KEY_THEME_MODE)
                initialColor = securePreferences.getPlainString(SecurePreferences.KEY_ACCENT_COLOR)
                initialLang = securePreferences.getPlainString(SecurePreferences.KEY_APP_LANGUAGE)
                initialPremium = securePreferences.getBoolean(SecurePreferences.KEY_IS_PREMIUM, true)
                initialFontScale = securePreferences.getPlainString(SecurePreferences.KEY_FONT_SIZE_SCALE)
                initialSupportEmail = securePreferences.getPlainString(SecurePreferences.KEY_SUPPORT_EMAIL)
                initialSupportPhone = securePreferences.getPlainString(SecurePreferences.KEY_SUPPORT_PHONE)
                initialDevMode = securePreferences.getBoolean(SecurePreferences.KEY_DEVELOPER_MODE, false)
                
                initialNotifCardExpiry = securePreferences.getBoolean(SecurePreferences.KEY_NOTIF_CARD_EXPIRY, true)
                initialNotifWeakPasswords = securePreferences.getBoolean(SecurePreferences.KEY_NOTIF_WEAK_PASSWORDS, true)
                initialNotifAutoBackup = securePreferences.getBoolean(SecurePreferences.KEY_NOTIF_AUTO_BACKUP, true)
                initialNotifExpiryThreshold = securePreferences.getInt(SecurePreferences.KEY_NOTIF_EXPIRY_THRESHOLD, 30)
            }
        } catch (e: Exception) {
            // Fallback to defaults if runBlocking fails
        }

        _configState.value = AppConfigState(
            themeMode = initialTheme?.let { runCatching { ThemeMode.valueOf(it) }.getOrNull() } ?: ThemeMode.SYSTEM,
            accentColorHex = initialColor ?: "#FF9800",
            language = initialLang ?: "en",
            fontSizeScale = initialFontScale?.let { runCatching { FontSizeScale.valueOf(it) }.getOrNull() } ?: FontSizeScale.MEDIUM,
            isPremium = initialPremium,
            supportEmail = initialSupportEmail ?: "hazemelhosary3@gmail.com",
            supportPhone = initialSupportPhone ?: "+201234567890",
            isDeveloperMode = initialDevMode,
            notifCardExpiry = initialNotifCardExpiry,
            notifWeakPasswords = initialNotifWeakPasswords,
            notifAutoBackup = initialNotifAutoBackup,
            notifExpiryThreshold = initialNotifExpiryThreshold
        )

        loadConfig()
    }

    private fun loadConfig() {
        viewModelScope.launch {
            combine(
                securePreferences.getPlainStringFlow(SecurePreferences.KEY_THEME_MODE),
                securePreferences.getPlainStringFlow(SecurePreferences.KEY_ACCENT_COLOR),
                securePreferences.getPlainStringFlow(SecurePreferences.KEY_APP_LANGUAGE),
                securePreferences.getBooleanFlow(SecurePreferences.KEY_IS_PREMIUM, true),
                securePreferences.getPlainStringFlow(SecurePreferences.KEY_FONT_SIZE_SCALE),
                securePreferences.getPlainStringFlow(SecurePreferences.KEY_SUPPORT_EMAIL),
                securePreferences.getPlainStringFlow(SecurePreferences.KEY_SUPPORT_PHONE),
                securePreferences.getBooleanFlow(SecurePreferences.KEY_DEVELOPER_MODE, false),
                securePreferences.getBooleanFlow(SecurePreferences.KEY_NOTIF_CARD_EXPIRY, true),
                securePreferences.getBooleanFlow(SecurePreferences.KEY_NOTIF_WEAK_PASSWORDS, true),
                securePreferences.getBooleanFlow(SecurePreferences.KEY_NOTIF_AUTO_BACKUP, true),
                securePreferences.getIntFlow(SecurePreferences.KEY_NOTIF_EXPIRY_THRESHOLD, 30)
            ) { values ->
                val theme = values[0] as? String
                val color = values[1] as? String
                val lang = values[2] as? String
                val isPremium = values[3] as? Boolean ?: true
                val fontScale = values[4] as? String
                val email = values[5] as? String
                val phone = values[6] as? String
                val devMode = values[7] as? Boolean ?: false
                val cardExpiry = values[8] as? Boolean ?: true
                val weakPwds = values[9] as? Boolean ?: true
                val autoBkp = values[10] as? Boolean ?: true
                val threshold = values[11] as? Int ?: 30
                
                AppConfigState(
                    themeMode = theme?.let { runCatching { ThemeMode.valueOf(it) }.getOrNull() } ?: ThemeMode.SYSTEM,
                    accentColorHex = color ?: "#FF9800",
                    language = lang ?: "en",
                    fontSizeScale = fontScale?.let { runCatching { FontSizeScale.valueOf(it) }.getOrNull() } ?: FontSizeScale.MEDIUM,
                    isPremium = isPremium,
                    supportEmail = email ?: "hazemelhosary3@gmail.com",
                    supportPhone = phone ?: "+201234567890",
                    isDeveloperMode = devMode,
                    notifCardExpiry = cardExpiry,
                    notifWeakPasswords = weakPwds,
                    notifAutoBackup = autoBkp,
                    notifExpiryThreshold = threshold
                )
            }.collect {
                _configState.value = it
            }
        }
    }

    fun setThemeMode(mode: ThemeMode) {
        viewModelScope.launch {
            securePreferences.putPlainString(SecurePreferences.KEY_THEME_MODE, mode.name)
        }
    }

    fun setAccentColor(hex: String) {
        viewModelScope.launch {
            securePreferences.putPlainString(SecurePreferences.KEY_ACCENT_COLOR, hex)
        }
    }

    fun setLanguage(lang: String) {
        viewModelScope.launch {
            securePreferences.putPlainString(SecurePreferences.KEY_APP_LANGUAGE, lang)
        }
    }

    fun setFontSizeScale(scale: FontSizeScale) {
        viewModelScope.launch {
            securePreferences.putPlainString(SecurePreferences.KEY_FONT_SIZE_SCALE, scale.name)
        }
    }

    fun unlockPremium() {
        viewModelScope.launch {
            securePreferences.putBoolean(SecurePreferences.KEY_IS_PREMIUM, true)
        }
    }

    fun setSupportEmail(email: String) {
        viewModelScope.launch {
            securePreferences.putPlainString(SecurePreferences.KEY_SUPPORT_EMAIL, email)
        }
    }

    fun setSupportPhone(phone: String) {
        viewModelScope.launch {
            securePreferences.putPlainString(SecurePreferences.KEY_SUPPORT_PHONE, phone)
        }
    }

    fun setDeveloperMode(enabled: Boolean) {
        viewModelScope.launch {
            securePreferences.putBoolean(SecurePreferences.KEY_DEVELOPER_MODE, enabled)
        }
    }

    fun setNotifCardExpiry(enabled: Boolean) {
        viewModelScope.launch {
            securePreferences.putBoolean(SecurePreferences.KEY_NOTIF_CARD_EXPIRY, enabled)
        }
    }

    fun setNotifWeakPasswords(enabled: Boolean) {
        viewModelScope.launch {
            securePreferences.putBoolean(SecurePreferences.KEY_NOTIF_WEAK_PASSWORDS, enabled)
        }
    }

    fun setNotifAutoBackup(enabled: Boolean) {
        viewModelScope.launch {
            securePreferences.putBoolean(SecurePreferences.KEY_NOTIF_AUTO_BACKUP, enabled)
        }
    }

    fun setNotifExpiryThreshold(days: Int) {
        viewModelScope.launch {
            securePreferences.putInt(SecurePreferences.KEY_NOTIF_EXPIRY_THRESHOLD, days)
        }
    }
}
