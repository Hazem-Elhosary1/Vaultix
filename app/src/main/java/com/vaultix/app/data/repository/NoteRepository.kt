package com.vaultix.app.data.repository

import com.vaultix.app.data.local.dao.NoteDao
import com.vaultix.app.data.local.entity.NoteEntity
import com.vaultix.app.data.model.Note
import com.vaultix.app.security.CryptoManager
import com.vaultix.app.security.KeystoreManager
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NoteRepository @Inject constructor(
    private val noteDao: NoteDao,
    private val cryptoManager: CryptoManager
) {
    private val key = KeystoreManager.getOrCreateDatabaseKey()

    fun getAllNotes(): Flow<List<Note>> {
        return noteDao.getAllNotes().map { entities ->
            entities.filter { it.isFake == com.vaultix.app.security.VaultSession.isFakeVaultActive }
                .mapNotNull { it.toDecrypted() }
        }
    }

    suspend fun getNoteById(id: String): Note? {
        val entity = noteDao.getNoteById(id) ?: return null
        if (entity.isFake != com.vaultix.app.security.VaultSession.isFakeVaultActive) return null
        return entity.toDecrypted()
    }

    suspend fun insertNote(note: Note) {
        noteDao.insertNote(note.toEncrypted())
    }

    suspend fun updateNote(note: Note) {
        noteDao.updateNote(note.toEncrypted())
    }

    suspend fun deleteNote(id: String) {
        noteDao.deleteNoteById(id)
    }

    suspend fun deleteAllNotes() {
        noteDao.deleteAllNotes()
    }

    suspend fun getNoteCount(): Int = noteDao.getNoteCount()

    private fun NoteEntity.toDecrypted(): Note? {
        return try {
            Note(
                id = id,
                title = cryptoManager.decrypt(title, key),
                content = cryptoManager.decrypt(content, key),
                color = color,
                isFavorite = isFavorite,
                createdAt = createdAt,
                updatedAt = updatedAt,
                keyVersion = keyVersion
            )
        } catch (e: Exception) {
            null
        }
    }

    private fun Note.toEncrypted() = NoteEntity(
        id = id.ifEmpty { UUID.randomUUID().toString() },
        title = cryptoManager.encrypt(title, key),
        content = cryptoManager.encrypt(content, key),
        color = color,
        isFavorite = isFavorite,
        createdAt = createdAt,
        updatedAt = updatedAt,
        keyVersion = keyVersion,
        isFake = com.vaultix.app.security.VaultSession.isFakeVaultActive
    )
}
