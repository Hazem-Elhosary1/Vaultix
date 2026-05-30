package com.vaultix.app.data.local.dao

import androidx.room.*
import com.vaultix.app.data.local.entity.CardEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface CardDao {
    @Query("SELECT * FROM cards ORDER BY updatedAt DESC")
    fun getAllCards(): Flow<List<CardEntity>>

    @Query("SELECT * FROM cards WHERE isFavorite = 1 ORDER BY updatedAt DESC")
    fun getFavoriteCards(): Flow<List<CardEntity>>

    @Query("SELECT * FROM cards WHERE id = :id LIMIT 1")
    suspend fun getCardById(id: String): CardEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCard(card: CardEntity)

    @Update
    suspend fun updateCard(card: CardEntity)

    @Delete
    suspend fun deleteCard(card: CardEntity)

    @Query("DELETE FROM cards WHERE id = :id")
    suspend fun deleteCardById(id: String)

    @Query("DELETE FROM cards")
    suspend fun deleteAllCards()

    @Query("SELECT COUNT(*) FROM cards")
    suspend fun getCardCount(): Int
    @Query("SELECT * FROM cards WHERE expiryTimestamp IS NOT NULL AND expiryTimestamp <= :threshold AND isFake = 0")
    suspend fun getExpiringCards(threshold: Long): List<CardEntity>
}
