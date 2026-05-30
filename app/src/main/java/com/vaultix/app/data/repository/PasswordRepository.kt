package com.vaultix.app.data.repository

import com.vaultix.app.data.local.dao.PasswordDao
import com.vaultix.app.data.local.entity.PasswordEntity
import com.vaultix.app.data.model.Password
import com.vaultix.app.security.CryptoManager
import com.vaultix.app.security.KeystoreManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.flowOn
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PasswordRepository @Inject constructor(
    private val passwordDao: PasswordDao,
    private val cryptoManager: CryptoManager
) {
    private val key = KeystoreManager.getOrCreateDatabaseKey()

    fun getAllPasswords(): Flow<List<Password>> {
        return passwordDao.getAllPasswords().map { entities ->
            entities.filter { it.isFake == com.vaultix.app.security.VaultSession.isFakeVaultActive }
                .mapNotNull { it.toDecrypted() }
        }.flowOn(Dispatchers.Default)
    }

    fun getFavoritePasswords(): Flow<List<Password>> {
        return passwordDao.getFavoritePasswords().map { entities ->
            entities.filter { it.isFake == com.vaultix.app.security.VaultSession.isFakeVaultActive }
                .mapNotNull { it.toDecrypted() }
        }.flowOn(Dispatchers.Default)
    }

    suspend fun getPasswordById(id: String): Password? {
        val entity = passwordDao.getPasswordById(id) ?: return null
        if (entity.isFake != com.vaultix.app.security.VaultSession.isFakeVaultActive) return null
        return entity.toDecrypted()
    }

    suspend fun insertPassword(password: Password) {
        passwordDao.insertPassword(password.toEncrypted())
    }

    suspend fun updatePassword(newPassword: Password) {
        val existing = passwordDao.getPasswordById(newPassword.id)?.toDecrypted()
        val updatedHistory = if (existing != null && !existing.password.contentEquals(newPassword.password)) {
            // Password changed, add old one to history
            (listOf(existing.password) + newPassword.passwordHistory).take(3)
        } else {
            newPassword.passwordHistory
        }
        
        passwordDao.updatePassword(newPassword.copy(passwordHistory = updatedHistory).toEncrypted())
    }

    suspend fun deletePassword(id: String) {
        passwordDao.deletePasswordById(id)
    }

    suspend fun deleteAllPasswords() {
        passwordDao.deleteAllPasswords()
    }

    suspend fun getPasswordCount(): Int = passwordDao.getPasswordCount()

    private fun PasswordEntity.toDecrypted(): Password? {
        return try {
            Password(
                id = id,
                title = cryptoManager.decrypt(title, key),
                username = cryptoManager.decrypt(username, key),
                password = cryptoManager.decryptToChars(password, key),
                website = cryptoManager.decrypt(website, key),
                appPackageName = appPackageName,
                notes = cryptoManager.decrypt(notes, key),
                passwordStrength = passwordStrength,
                isFavorite = isFavorite,
                passwordHistory = if (passwordHistory.isEmpty()) emptyList() else {
                    cryptoManager.decrypt(passwordHistory, key).split("|").filter { it.isNotEmpty() }.map { it.toCharArray() }
                },
                createdAt = createdAt,
                updatedAt = updatedAt,
                expiresAt = expiresAt,
                keyVersion = keyVersion
            )
        } catch (e: Exception) {
            null
        }
    }

    private fun Password.toEncrypted() = PasswordEntity(
        id = id.ifEmpty { UUID.randomUUID().toString() },
        title = cryptoManager.encrypt(title, key),
        username = cryptoManager.encrypt(username, key),
        password = cryptoManager.encrypt(password, key),
        website = cryptoManager.encrypt(website, key),
        appPackageName = appPackageName,
        notes = cryptoManager.encrypt(notes, key),
        passwordStrength = passwordStrength,
        isFavorite = isFavorite,
        passwordHistory = if (passwordHistory.isEmpty()) "" else {
            cryptoManager.encrypt(passwordHistory.joinToString("|") { it.concatToString() }, key)
        },
        createdAt = createdAt,
        updatedAt = updatedAt,
        expiresAt = expiresAt,
        keyVersion = keyVersion,
        isFake = com.vaultix.app.security.VaultSession.isFakeVaultActive
    )
}
