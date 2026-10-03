package com.dazaike.photovault.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "vault_items")
data class VaultItemEntity(
    @PrimaryKey val id: String,
    val originalName: String,
    val mimeType: String,
    val addedAtEpochMs: Long,
    val isDeleted: Boolean = false,
    val deletedAtEpochMs: Long? = null,
    /** Video length in ms. null = not measured yet, 0 = could not be determined, always null for images. */
    val durationMs: Long? = null,
)
