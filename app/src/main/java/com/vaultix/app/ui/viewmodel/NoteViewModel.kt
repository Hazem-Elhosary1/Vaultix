package com.vaultix.app.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vaultix.app.data.model.Note
import com.vaultix.app.data.repository.NoteRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

data class NoteUiState(
    val notes: List<Note> = emptyList(),
    val searchQuery: String = "",
    val isLoading: Boolean = false,
    val error: String? = null
)

@HiltViewModel
class NoteViewModel @Inject constructor(
    private val noteRepository: NoteRepository
) : ViewModel() {

    private val _searchQuery = MutableStateFlow("")
    private val _uiState = MutableStateFlow(NoteUiState(isLoading = true))
    val uiState: StateFlow<NoteUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            combine(noteRepository.getAllNotes(), _searchQuery) { notes, query ->
                val filtered = if (query.isBlank()) notes
                else notes.filter { it.title.contains(query, ignoreCase = true) || it.content.contains(query, ignoreCase = true) }
                val sorted = filtered.sortedWith(compareByDescending<Note> { it.isPinned }.thenByDescending { it.updatedAt })
                NoteUiState(notes = sorted, searchQuery = query, isLoading = false)
            }.collect { _uiState.value = it }
        }
    }

    fun setSearchQuery(query: String) { _searchQuery.value = query }

    fun insertNote(note: Note) {
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            noteRepository.insertNote(note.copy(id = UUID.randomUUID().toString(), createdAt = now, updatedAt = now))
        }
    }

    fun updateNote(note: Note) {
        viewModelScope.launch {
            noteRepository.updateNote(note.copy(updatedAt = System.currentTimeMillis()))
        }
    }

    fun deleteNote(id: String) {
        viewModelScope.launch { noteRepository.deleteNote(id) }
    }

    fun toggleFavorite(note: Note) {
        viewModelScope.launch {
            noteRepository.updateNote(note.copy(isFavorite = !note.isFavorite))
        }
    }

    fun togglePin(note: Note) {
        viewModelScope.launch {
            noteRepository.updateNote(note.copy(isPinned = !note.isPinned))
        }
    }
}
