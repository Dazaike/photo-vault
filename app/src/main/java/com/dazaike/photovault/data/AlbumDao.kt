package com.dazaike.photovault.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface AlbumDao {
    @Query("SELECT * FROM albums ORDER BY createdAtEpochMs DESC")
    fun observeAlbums(): Flow<List<AlbumEntity>>

    @Query("SELECT * FROM albums")
    suspend fun allAlbums(): List<AlbumEntity>

    @Query("SELECT * FROM album_items")
    suspend fun allCrossRefs(): List<AlbumItemCrossRef>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAlbums(albums: List<AlbumEntity>)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertCrossRefs(refs: List<AlbumItemCrossRef>)

    @Insert
    suspend fun insertAlbum(album: AlbumEntity)

    @Delete
    suspend fun deleteAlbum(album: AlbumEntity)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun addToAlbum(crossRef: AlbumItemCrossRef)

    @Query("DELETE FROM album_items WHERE albumId = :albumId AND itemId = :itemId")
    suspend fun removeFromAlbum(albumId: String, itemId: String)
    @Query("UPDATE albums SET name = :name WHERE id = :id")
    suspend fun renameAlbum(id: String, name: String)

    @Query("DELETE FROM album_items WHERE itemId = :itemId")
    suspend fun removeFromAllAlbums(itemId: String)

    @Query("DELETE FROM album_items WHERE itemId IN (:itemIds)")
    suspend fun removeItemsFromAllAlbums(itemIds: List<String>)


    @Query(
        """
        SELECT vault_items.* FROM vault_items
        INNER JOIN album_items ON vault_items.id = album_items.itemId
        WHERE album_items.albumId = :albumId AND vault_items.isDeleted = 0
        ORDER BY vault_items.addedAtEpochMs DESC
        """,
    )
    fun observeItemsInAlbum(albumId: String): Flow<List<VaultItemEntity>>
}
