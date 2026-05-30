package com.vaultix.app.data.local.dao

import androidx.room.*
import com.vaultix.app.data.local.entity.PasswordEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface PasswordDao {
    @Query("SELECT * FROM passwords ORDER BY updatedAt DESC")
    fun getAllPasswords(): Flow<List<PasswordEntity>>

    @Query("SELECT * FROM passwords")
    suspend fun getAllPasswordsList(): List<PasswordEntity>

    @Query("SELECT * FROM passwords WHERE isFavorite = 1 ORDER BY updatedAt DESC")
    fun getFavoritePasswords(): Flow<List<PasswordEntity>>

    @Query("SELECT * FROM passwords WHERE id = :id LIMIT 1")
    suspend fun getPasswordById(id: String): PasswordEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPassword(password: PasswordEntity)

    @Update
    suspend fun updatePassword(password: PasswordEntity)

    @Delete
    suspend fun deletePassword(password: PasswordEntity)

    @Query("DELETE FROM passwords WHERE id = :id")
    suspend fun deletePasswordById(id: String)

    @Query("DELETE FROM passwords")
    suspend fun deleteAllPasswords()

    @Query("SELECT * FROM passwords WHERE appPackageName = :packageName OR appPackageName = ''")
    suspend fun getPasswordsByMatch(packageName: String): List<PasswordEntity>

    @Query("SELECT COUNT(*) FROM passwords")
    suspend fun getPasswordCount(): Int

    @Query("SELECT * FROM passwords WHERE expiresAt IS NOT NULL AND expiresAt <= :threshold AND isFake = 0")
    suspend fun getExpiringPasswords(threshold: Long): List<PasswordEntity>
}
