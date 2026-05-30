package com.vaultix.app.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vaultix.app.data.model.*
import com.vaultix.app.data.repository.*
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import javax.inject.Inject

data class GlobalSearchState(
    val query: String = "",
    val passwordResults: List<Password> = emptyList(),
    val cardResults: List<Card> = emptyList(),
    val noteResults: List<Note> = emptyList(),
    val identityResults: List<Identity> = emptyList()
)

@HiltViewModel
class GlobalSearchViewModel @Inject constructor(
    private val passwordRepository: PasswordRepository,
    private val cardRepository: CardRepository,
    private val noteRepository: NoteRepository,
    private val identityRepository: IdentityRepository
) : ViewModel() {

    private val _searchQuery = MutableStateFlow("")
    val searchQuery = _searchQuery.asStateFlow()

    val searchState = _searchQuery.flatMapLatest { query ->
        if (query.length < 2) {
            flowOf(GlobalSearchState(query = query))
        } else {
            combine(
                passwordRepository.getAllPasswords(),
                cardRepository.getAllCards(),
                noteRepository.getAllNotes(),
                identityRepository.getAllIdentities()
            ) { passwords, cards, notes, identities ->
                GlobalSearchState(
                    query = query,
                    passwordResults = passwords.filter { it.title.contains(query, true) || it.username.contains(query, true) },
                    cardResults = cards.filter { it.cardName.contains(query, true) || it.holderName.contains(query, true) },
                    noteResults = notes.filter { it.title.contains(query, true) || it.content.contains(query, true) },
                    identityResults = identities.filter { it.documentName.contains(query, true) || it.fullName.contains(query, true) }
                )
            }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), GlobalSearchState())

    fun onQueryChange(newQuery: String) {
        _searchQuery.value = newQuery
    }
}
