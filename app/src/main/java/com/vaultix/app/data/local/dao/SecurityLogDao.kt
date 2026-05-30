package com.vaultix.app.data.local.dao

import androidx.room.*
import com.vaultix.app.data.local.entity.SecurityLogEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SecurityLogDao {
    @Query("SELECT * FROM security_logs ORDER BY timestamp DESC LIMIT 100")
    fun getRecentLogs(): Flow<List<SecurityLogEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLog(log: SecurityLogEntity)

    @Query("DELETE FROM security_logs")
    suspend fun clearLogs()
    
    @Query("SELECT COUNT(*) FROM security_logs WHERE eventType = :eventType AND timestamp > :since")
    suspend fun countEventsSince(eventType: String, since: Long): Int
}
