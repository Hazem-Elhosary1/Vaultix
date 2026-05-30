package com.vaultix.app.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vaultix.app.data.model.Card
import com.vaultix.app.data.repository.CardRepository
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
        }
    }

    fun updateCard(card: Card) {
        viewModelScope.launch {
            cardRepository.updateCard(card.copy(updatedAt = System.currentTimeMillis()))
        }
    }

    fun deleteCard(id: String) {
        viewModelScope.launch {
            cardRepository.deleteCard(id)
        }
    }

    fun toggleFavorite(card: Card) {
        viewModelScope.launch {
            cardRepository.updateCard(card.copy(isFavorite = !card.isFavorite))
        }
    }
}
