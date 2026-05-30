package com.vaultix.app.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vaultix.app.data.repository.CardRepository
import com.vaultix.app.data.repository.PasswordRepository
import com.vaultix.app.data.repository.IdentityRepository
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject
import dagger.hilt.android.lifecycle.HiltViewModel

data class SecurityHealthState(
    val score: Int = 100,
    val weakPasswordsCount: Int = 0,
    val expiredItemsCount: Int = 0,
    val totalItems: Int = 0
)

@HiltViewModel
class SecurityHealthViewModel @Inject constructor(
    private val passwordRepository: PasswordRepository,
    private val cardRepository: CardRepository,
    private val identityRepository: IdentityRepository
) : ViewModel() {

    private val _healthState = MutableStateFlow(SecurityHealthState())
    val healthState: StateFlow<SecurityHealthState> = _healthState.asStateFlow()

    init {
        combine(
            passwordRepository.getAllPasswords(),
            cardRepository.getAllCards(),
            identityRepository.getAllIdentities()
        ) { passwords, cards, identities ->
            val weak = passwords.count { it.passwordStrength < 3 }
            val expiredCards = cards.count { it.isExpired }
            // Basic expiry check for identities
            val expiredIDs = identities.count { id ->
                try {
                    val parts = id.expiryDate.split("/", "-")
                    val cal = java.util.Calendar.getInstance()
                    if (parts[0].length == 4) cal.set(parts[0].toInt(), parts[1].toInt()-1, parts[2].toInt())
                    else cal.set(parts[2].toInt(), parts[1].toInt()-1, parts[0].toInt())
                    java.util.Calendar.getInstance().after(cal)
                } catch(_: Exception) { false }
            }
            
            val total = passwords.size + cards.size + identities.size
            val expired = expiredCards + expiredIDs
            
            // Logic for score
            var score = 100
            if (total > 0) {
                score -= (weak * 10)
                score -= (expired * 15)
            }
            
            SecurityHealthState(
                score = score.coerceIn(0, 100),
                weakPasswordsCount = weak,
                expiredItemsCount = expired,
                totalItems = total
            )
        }.onEach { _healthState.value = it }.launchIn(viewModelScope)
    }
}
