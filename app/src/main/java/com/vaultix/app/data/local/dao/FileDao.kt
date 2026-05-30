package com.vaultix.app.data.local.dao

import androidx.room.*
import com.vaultix.app.data.local.entity.FileEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface FileDao {
    @Query("SELECT * FROM files ORDER BY updatedAt DESC")
    fun getAllFiles(): Flow<List<FileEntity>>

    @Query("SELECT * FROM files WHERE id = :id LIMIT 1")
    suspend fun getFileById(id: String): FileEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFile(file: FileEntity)

    @Update
    suspend fun updateFile(file: FileEntity)

    @Delete
    suspend fun deleteFile(file: FileEntity)

    @Query("DELETE FROM files WHERE id = :id")
    suspend fun deleteFileById(id: String)

    @Query("DELETE FROM files")
    suspend fun deleteAllFiles()

    @Query("SELECT COUNT(*) FROM files")
    suspend fun getFileCount(): Int
}
