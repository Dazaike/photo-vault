package com.dazaike.photovault.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface VaultDao {
    @Query("SELECT * FROM vault_items WHERE isDeleted = 0 ORDER BY addedAtEpochMs DESC")
    fun observeActive(): Flow<List<VaultItemEntity>>
    @Query("SELECT * FROM vault_items WHERE isDeleted = 0 AND id NOT IN (SELECT itemId FROM album_items) ORDER BY addedAtEpochMs DESC")
    fun observeUnfiled(): Flow<List<VaultItemEntity>>


    @Query("SELECT * FROM vault_items WHERE isDeleted = 1 ORDER BY deletedAtEpochMs DESC")
    fun observeTrash(): Flow<List<VaultItemEntity>>

    @Query("SELECT * FROM vault_items WHERE isDeleted = 1 AND deletedAtEpochMs < :cutoffEpochMs")
    suspend fun trashOlderThan(cutoffEpochMs: Long): List<VaultItemEntity>

    @Insert
    suspend fun insert(item: VaultItemEntity)

    @Query("UPDATE vault_items SET isDeleted = 1, deletedAtEpochMs = :deletedAtEpochMs WHERE id = :id")
    suspend fun softDelete(id: String, deletedAtEpochMs: Long)

    @Query("UPDATE vault_items SET isDeleted = 0, deletedAtEpochMs = NULL WHERE id = :id")
    suspend fun restore(id: String)

    @Query("UPDATE vault_items SET durationMs = :durationMs WHERE id = :id")
    suspend fun setDuration(id: String, durationMs: Long)

    @Delete
    suspend fun delete(item: VaultItemEntity)

    @Query("SELECT * FROM vault_items")
    suspend fun allItems(): List<VaultItemEntity>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(items: List<VaultItemEntity>)
}
