package com.vaultix.app.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vaultix.app.data.model.Card
import com.vaultix.app.data.repository.CardRepository
import com.vaultix.app.debug.DebugCategory
import com.vaultix.app.debug.DebugEventBus
import com.vaultix.app.debug.DebugSeverity
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

data class CardUiState(
    val cards: List<Card> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null
)

@HiltViewModel
class CardViewModel @Inject constructor(
    private val cardRepository: CardRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(CardUiState(isLoading = true))
    val uiState: StateFlow<CardUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            cardRepository.getAllCards().collect { cards ->
                _uiState.update { it.copy(cards = cards, isLoading = false) }
            }
        }
    }

    fun insertCard(card: Card) {
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            cardRepository.insertCard(card.copy(id = UUID.randomUUID().toString(), createdAt = now, updatedAt = now))
            DebugEventBus.log(
                category  = DebugCategory.CRUD,
                eventType = "CARD_CREATED",
                details   = "holder=${card.holderName}, type=${card.cardType}",
                source    = "CardViewModel"
            )
        }
    }

    fun updateCard(card: Card) {
        viewModelScope.launch {
            cardRepository.updateCard(card.copy(updatedAt = System.currentTimeMillis()))
            DebugEventBus.log(
                category  = DebugCategory.CRUD,
                eventType = "CARD_UPDATED",
                details   = "id=${card.id}, holder=${card.holderName}",
                source    = "CardViewModel"
            )
        }
    }

    fun deleteCard(id: String) {
        viewModelScope.launch {
            cardRepository.deleteCard(id)
            DebugEventBus.log(
                category  = DebugCategory.CRUD,
                eventType = "CARD_DELETED",
                details   = "id=$id",
                severity  = DebugSeverity.WARNING,
                source    = "CardViewModel"
            )
        }
    }

    fun toggleFavorite(card: Card) {
        viewModelScope.launch {
            cardRepository.updateCard(card.copy(isFavorite = !card.isFavorite))
            DebugEventBus.log(
                category  = DebugCategory.CRUD,
                eventType = "CARD_FAVORITE_TOGGLED",
                details   = "id=${card.id}, isFavorite=${!card.isFavorite}",
                source    = "CardViewModel"
            )
        }
    }
}
