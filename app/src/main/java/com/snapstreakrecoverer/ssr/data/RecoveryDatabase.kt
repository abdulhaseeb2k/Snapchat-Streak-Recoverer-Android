package com.snapstreakrecoverer.ssr.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(entities = [Profile::class, Friend::class], version = 2, exportSchema = false)
abstract class RecoveryDatabase : RoomDatabase() {
    abstract fun recoveryDao(): RecoveryDao

    companion object {
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE Profile ADD COLUMN syncId TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE Profile ADD COLUMN updatedAt INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE Profile ADD COLUMN isDeleted INTEGER NOT NULL DEFAULT 0")

                db.execSQL("ALTER TABLE Friend ADD COLUMN syncId TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE Friend ADD COLUMN profileSyncId TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE Friend ADD COLUMN updatedAt INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE Friend ADD COLUMN isDeleted INTEGER NOT NULL DEFAULT 0")

                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_Profile_syncId` ON `Profile` (`syncId`)")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_Friend_syncId` ON `Friend` (`syncId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_Friend_profileId` ON `Friend` (`profileId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_Friend_profileSyncId` ON `Friend` (`profileSyncId`)")
            }
        }

        @Volatile
        private var INSTANCE: RecoveryDatabase? = null

        fun getDatabase(context: Context): RecoveryDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    RecoveryDatabase::class.java,
                    "recovery_database"
                )
                .addMigrations(MIGRATION_1_2)
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
