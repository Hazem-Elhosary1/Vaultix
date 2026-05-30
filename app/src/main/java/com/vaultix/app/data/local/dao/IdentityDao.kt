package com.vaultix.app.data.local.dao

import androidx.room.*
import com.vaultix.app.data.local.entity.IdentityEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface IdentityDao {
    @Query("SELECT * FROM identities ORDER BY updatedAt DESC")
    fun getAllIdentities(): Flow<List<IdentityEntity>>

    @Query("SELECT * FROM identities WHERE id = :id LIMIT 1")
    suspend fun getIdentityById(id: String): IdentityEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertIdentity(identity: IdentityEntity)

    @Update
    suspend fun updateIdentity(identity: IdentityEntity)

    @Delete
    suspend fun deleteIdentity(identity: IdentityEntity)

    @Query("DELETE FROM identities WHERE id = :id")
    suspend fun deleteIdentityById(id: String)

    @Query("DELETE FROM identities")
    suspend fun deleteAllIdentities()

    @Query("SELECT COUNT(*) FROM identities")
    suspend fun getIdentityCount(): Int
    @Query("SELECT * FROM identities WHERE expiryTimestamp IS NOT NULL AND expiryTimestamp <= :threshold AND isFake = 0")
    suspend fun getExpiringIdentities(threshold: Long): List<IdentityEntity>
}
