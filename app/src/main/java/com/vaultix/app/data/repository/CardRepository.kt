package com.vaultix.app.data.repository

import com.vaultix.app.data.local.dao.CardDao
import com.vaultix.app.data.local.entity.CardEntity
import com.vaultix.app.data.model.Card
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
class CardRepository @Inject constructor(
    private val cardDao: CardDao,
    private val cryptoManager: CryptoManager
) {
    private val key = KeystoreManager.getOrCreateDatabaseKey()

    fun getAllCards(): Flow<List<Card>> {
        return cardDao.getAllCards().map { entities ->
            entities.filter { it.isFake == com.vaultix.app.security.VaultSession.isFakeVaultActive }
                .mapNotNull { it.toDecrypted() }
        }.flowOn(Dispatchers.Default)
    }

    suspend fun getCardById(id: String): Card? {
        val entity = cardDao.getCardById(id) ?: return null
        if (entity.isFake != com.vaultix.app.security.VaultSession.isFakeVaultActive) return null
        return entity.toDecrypted()
    }

    suspend fun insertCard(card: Card) {
        cardDao.insertCard(card.toEncrypted())
    }

    suspend fun updateCard(card: Card) {
        cardDao.updateCard(card.toEncrypted())
    }

    suspend fun deleteCard(id: String) {
        cardDao.deleteCardById(id)
    }

    suspend fun deleteAllCards() {
        cardDao.deleteAllCards()
    }

    suspend fun getCardCount(): Int = cardDao.getCardCount()

    private fun CardEntity.toDecrypted(): Card? {
        return try {
            Card(
                id = id,
                cardName = cryptoManager.decrypt(cardName, key),
                holderName = cryptoManager.decrypt(holderName, key),
                cardNumber = cryptoManager.decrypt(cardNumber, key),
                expiryMonth = cryptoManager.decrypt(expiryMonth, key),
                expiryYear = cryptoManager.decrypt(expiryYear, key),
                cvv = cryptoManager.decrypt(cvv, key),
                cardType = cryptoManager.decrypt(cardType, key),
                notes = cryptoManager.decrypt(notes, key),
                isFavorite = isFavorite,
                createdAt = createdAt,
                updatedAt = updatedAt,
                keyVersion = keyVersion
            )
        } catch (e: Exception) {
            null
        }
    }

    private fun Card.toEncrypted() = CardEntity(
        id = id.ifEmpty { UUID.randomUUID().toString() },
        cardName = cryptoManager.encrypt(cardName, key),
        holderName = cryptoManager.encrypt(holderName, key),
        cardNumber = cryptoManager.encrypt(cardNumber, key),
        expiryMonth = cryptoManager.encrypt(expiryMonth, key),
        expiryYear = cryptoManager.encrypt(expiryYear, key),
        cvv = cryptoManager.encrypt(cvv, key),
        cardType = cryptoManager.encrypt(cardType, key),
        notes = cryptoManager.encrypt(notes, key),
        isFavorite = isFavorite,
        createdAt = createdAt,
        updatedAt = updatedAt,
        keyVersion = keyVersion,
        isFake = com.vaultix.app.security.VaultSession.isFakeVaultActive
    )
}
