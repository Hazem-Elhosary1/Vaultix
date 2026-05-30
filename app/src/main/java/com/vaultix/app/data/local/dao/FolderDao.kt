package com.vaultix.app.data.local.dao

import androidx.room.*
import com.vaultix.app.data.local.entity.FolderEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface FolderDao {
    @Query("SELECT * FROM file_folders WHERE isFake = :isFake ORDER BY createdAt DESC")
    fun getAllFolders(isFake: Boolean = false): Flow<List<FolderEntity>>

    @Query("SELECT * FROM file_folders WHERE parentFolderId = :parentId AND isFake = :isFake")
    fun getFoldersByParent(parentId: String?, isFake: Boolean = false): Flow<List<FolderEntity>>

    @Query("SELECT * FROM file_folders WHERE id = :id")
    suspend fun getFolderById(id: String): FolderEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFolder(folder: FolderEntity)

    @Update
    suspend fun updateFolder(folder: FolderEntity)

    @Delete
    suspend fun deleteFolder(folder: FolderEntity)
    
    @Query("DELETE FROM file_folders WHERE id = :id")
    suspend fun deleteFolderById(id: String)

    @Query("DELETE FROM file_folders")
    suspend fun deleteAllFolders()
}
