package com.dazaike.photovault.data

import android.content.Context
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

/**
 * Stores only filenames/timestamps/mime type/album links — no image bytes — so it
 * is left unencrypted; the actual photo content is protected by
 * [com.dazaike.photovault.crypto.VaultCrypto].
 *
 * `fallbackToDestructiveMigration` is intentional pre-release: there is no
 * shipped schema to preserve across the trash/albums column additions, and
 * definitions here still fully describe the data users care about (originals
 * live on disk keyed by id and are re-adoptable once a real migration path
 * matters).
 */
@Database(entities = [VaultItemEntity::class, AlbumEntity::class, AlbumItemCrossRef::class], version = 3)
abstract class VaultDatabase : RoomDatabase() {
    abstract fun vaultDao(): VaultDao
    abstract fun albumDao(): AlbumDao

    companion object {
        /** Adds the nullable video duration; existing rows keep their data and are backfilled lazily. */
        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE vault_items ADD COLUMN durationMs INTEGER")
            }
        }

        @Volatile
        private var instance: VaultDatabase? = null

        fun getInstance(context: Context): VaultDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    VaultDatabase::class.java,
                    "vault.db",
                ).addMigrations(MIGRATION_2_3).fallbackToDestructiveMigration().build().also { instance = it }
            }
    }
}
