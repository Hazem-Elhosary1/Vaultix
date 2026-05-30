package com.vaultix.app.data.repository

import com.vaultix.app.data.local.dao.IdentityDao
import com.vaultix.app.data.local.entity.IdentityEntity
import com.vaultix.app.data.model.Identity
import com.vaultix.app.security.CryptoManager
import com.vaultix.app.security.KeystoreManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.flowOn
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class IdentityRepository @Inject constructor(
    private val identityDao: IdentityDao,
    private val cryptoManager: CryptoManager
) {
    private val key
        get() = runCatching { KeystoreManager.getOrCreateDatabaseKey() }.getOrNull()

    fun getAllIdentities(): Flow<List<Identity>> {
        return identityDao.getAllIdentities().map { entities ->
            entities.filter { it.isFake == com.vaultix.app.security.VaultSession.isFakeVaultActive }
                .mapNotNull { it.toDecrypted() }
        }.flowOn(Dispatchers.Default).catch { emit(emptyList()) }
    }

    suspend fun getIdentityById(id: String): Identity? {
        return runCatching {
            val entity = identityDao.getIdentityById(id) ?: return null
            if (entity.isFake != com.vaultix.app.security.VaultSession.isFakeVaultActive) return null
            entity.toDecrypted()
        }.getOrNull()
    }

    suspend fun insertIdentity(identity: Identity) {
        identityDao.insertIdentity(identity.toEncrypted())
    }

    suspend fun updateIdentity(identity: Identity) {
        identityDao.updateIdentity(identity.toEncrypted())
    }

    suspend fun deleteIdentity(id: String) {
        identityDao.deleteIdentityById(id)
    }

    suspend fun deleteAllIdentities() {
        identityDao.deleteAllIdentities()
    }

    suspend fun getIdentityCount(): Int = identityDao.getIdentityCount()

    private fun IdentityEntity.toDecrypted(): Identity? {
        val databaseKey = key ?: return null
        return try {
            Identity(
                id = id,
                documentType = cryptoManager.decrypt(documentType, databaseKey),
                documentName = cryptoManager.decrypt(documentName, databaseKey),
                documentNumber = cryptoManager.decrypt(documentNumber, databaseKey),
                fullName = cryptoManager.decrypt(fullName, databaseKey),
                dateOfBirth = cryptoManager.decrypt(dateOfBirth, databaseKey),
                issuedBy = cryptoManager.decrypt(issuedBy, databaseKey),
                issuedDate = cryptoManager.decrypt(issuedDate, databaseKey),
                expiryDate = cryptoManager.decrypt(expiryDate, databaseKey),
                nationality = cryptoManager.decrypt(nationality, databaseKey),
                notes = cryptoManager.decrypt(notes, databaseKey),
                imagePaths = imagePaths?.split(",")?.filter { it.isNotBlank() } ?: emptyList(),
                isFavorite = isFavorite,
                createdAt = createdAt,
                updatedAt = updatedAt,
                keyVersion = keyVersion
            )
        } catch (e: Exception) {
            null
        }
    }

    private fun Identity.toEncrypted() = IdentityEntity(
        id = id.ifEmpty { UUID.randomUUID().toString() },
        documentType = cryptoManager.encrypt(documentType, key ?: throw IllegalStateException("Database key unavailable")),
        documentName = cryptoManager.encrypt(documentName, key ?: throw IllegalStateException("Database key unavailable")),
        documentNumber = cryptoManager.encrypt(documentNumber, key ?: throw IllegalStateException("Database key unavailable")),
        fullName = cryptoManager.encrypt(fullName, key ?: throw IllegalStateException("Database key unavailable")),
        dateOfBirth = cryptoManager.encrypt(dateOfBirth, key ?: throw IllegalStateException("Database key unavailable")),
        issuedBy = cryptoManager.encrypt(issuedBy, key ?: throw IllegalStateException("Database key unavailable")),
        issuedDate = cryptoManager.encrypt(issuedDate, key ?: throw IllegalStateException("Database key unavailable")),
        expiryDate = cryptoManager.encrypt(expiryDate, key ?: throw IllegalStateException("Database key unavailable")),
        nationality = cryptoManager.encrypt(nationality, key ?: throw IllegalStateException("Database key unavailable")),
        notes = cryptoManager.encrypt(notes, key ?: throw IllegalStateException("Database key unavailable")),
        imagePaths = imagePaths.joinToString(","),
        isFavorite = isFavorite,
        createdAt = createdAt,
        updatedAt = updatedAt,
        keyVersion = keyVersion,
        isFake = com.vaultix.app.security.VaultSession.isFakeVaultActive
    )
}
