package com.vaultix.app.data.repository

import com.vaultix.app.data.local.dao.*
import com.vaultix.app.data.local.entity.*
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class VaultRepository @Inject constructor(
    private val passwordDao: PasswordDao,
    private val cardDao: CardDao,
    private val noteDao: NoteDao,
    private val fileDao: FileDao,
    private val identityDao: IdentityDao,
    private val securityLogDao: SecurityLogDao,
    private val folderDao: FolderDao
) {

    suspend fun getAllVaultData(): VaultBackupData = coroutineScope {
        val passwordsDeferred = async { passwordDao.getAllPasswords().first() }
        val cardsDeferred = async { cardDao.getAllCards().first() }
        val notesDeferred = async { noteDao.getAllNotes().first() }
        val filesDeferred = async { fileDao.getAllFiles().first() }
        val identitiesDeferred = async { identityDao.getAllIdentities().first() }
        val securityLogsDeferred = async { securityLogDao.getRecentLogs().first() }
        val foldersDeferred = async { folderDao.getAllFolders().first() }

        VaultBackupData(
            passwords = passwordsDeferred.await(),
            cards = cardsDeferred.await(),
            notes = notesDeferred.await(),
            files = filesDeferred.await(),
            identities = identitiesDeferred.await(),
            securityLogs = securityLogsDeferred.await(),
            folders = foldersDeferred.await()
        )
    }

    data class VaultBackupData(
        val passwords: List<PasswordEntity> = emptyList(),
        val cards: List<CardEntity> = emptyList(),
        val notes: List<NoteEntity> = emptyList(),
        val files: List<FileEntity> = emptyList(),
        val identities: List<IdentityEntity> = emptyList(),
        val securityLogs: List<SecurityLogEntity> = emptyList(),
        val folders: List<FolderEntity> = emptyList()
    )
}
