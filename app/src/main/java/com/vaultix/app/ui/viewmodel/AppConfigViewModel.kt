package com.vaultix.app.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vaultix.app.security.SecurePreferences
import com.vaultix.app.ui.theme.FontSizeScale
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

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
        loadConfig()
    }

    private fun loadConfig() {
        viewModelScope.launch {
            combine(
                securePreferences.getPlainStringFlow(SecurePreferences.KEY_THEME_MODE),
                securePreferences.getPlainStringFlow(SecurePreferences.KEY_ACCENT_COLOR),
                securePreferences.getPlainStringFlow(SecurePreferences.KEY_APP_LANGUAGE),
                securePreferences.getBooleanFlow(SecurePreferences.KEY_IS_PREMIUM, true),
                securePreferences.getPlainStringFlow(SecurePreferences.KEY_FONT_SIZE_SCALE)
            ) { values ->
                val theme = values[0] as? String
                val color = values[1] as? String
                val lang = values[2] as? String
                val isPremium = values[3] as? Boolean ?: true
                val fontScale = values[4] as? String
                AppConfigState(
                    themeMode = theme?.let { ThemeMode.valueOf(it) } ?: ThemeMode.SYSTEM,
                    accentColorHex = color ?: "#FF9800",
                    language = lang ?: "en",
                    fontSizeScale = fontScale?.let { runCatching { FontSizeScale.valueOf(it) }.getOrNull() } ?: FontSizeScale.MEDIUM,
                    isPremium = isPremium
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
}
