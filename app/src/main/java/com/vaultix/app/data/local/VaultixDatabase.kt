package com.vaultix.app.data.local

import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import android.content.Context
import com.vaultix.app.data.local.dao.*
import com.vaultix.app.data.local.entity.*
import net.sqlcipher.database.SQLiteDatabase
import net.sqlcipher.database.SupportFactory

/**
 * Main Room database secured with SQLCipher (AES-256-CBC).
 * The passphrase is derived from the user's master password + Android Keystore key.
 */
@Database(
    entities = [
        PasswordEntity::class,
        CardEntity::class,
        NoteEntity::class,
        FileEntity::class,
        IdentityEntity::class,
        SecurityLogEntity::class,
        FolderEntity::class
    ],
    version = 10,
    exportSchema = false
)
abstract class VaultixDatabase : RoomDatabase() {

    abstract fun passwordDao(): PasswordDao
    abstract fun cardDao(): CardDao
    abstract fun noteDao(): NoteDao
    abstract fun fileDao(): FileDao
    abstract fun identityDao(): IdentityDao
    abstract fun securityLogDao(): SecurityLogDao
    abstract fun folderDao(): FolderDao

    companion object {
        val DB_NAME = com.vaultix.app.BuildConfig.DATABASE_NAME

        val MIGRATION_1_2 = object : androidx.room.migration.Migration(1, 2) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE passwords ADD COLUMN keyVersion INTEGER NOT NULL DEFAULT 1")
                db.execSQL("ALTER TABLE cards ADD COLUMN keyVersion INTEGER NOT NULL DEFAULT 1")
                db.execSQL("ALTER TABLE notes ADD COLUMN keyVersion INTEGER NOT NULL DEFAULT 1")
                db.execSQL("ALTER TABLE files ADD COLUMN keyVersion INTEGER NOT NULL DEFAULT 1")
                db.execSQL("ALTER TABLE identities ADD COLUMN keyVersion INTEGER NOT NULL DEFAULT 1")
            }
        }

        val MIGRATION_2_3 = object : androidx.room.migration.Migration(2, 3) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS security_logs (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        eventType TEXT NOT NULL,
                        details TEXT NOT NULL,
                        timestamp INTEGER NOT NULL,
                        severity TEXT NOT NULL
                    )
                """.trimIndent())
            }
        }

        val MIGRATION_3_4 = object : androidx.room.migration.Migration(3, 4) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE passwords ADD COLUMN isFake INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE cards ADD COLUMN isFake INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE notes ADD COLUMN isFake INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE files ADD COLUMN isFake INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE identities ADD COLUMN isFake INTEGER NOT NULL DEFAULT 0")
            }
        }

        val MIGRATION_4_5 = object : androidx.room.migration.Migration(4, 5) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                // Placeholder migration (no schema changes in v4->v5)
                // Can add additional security or field updates here if needed
            }
        }

        val MIGRATION_5_6 = object : androidx.room.migration.Migration(5, 6) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE passwords ADD COLUMN appPackageName TEXT NOT NULL DEFAULT ''")
            }
        }

        val MIGRATION_6_7 = object : androidx.room.migration.Migration(6, 7) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                // Placeholder
            }
        }

        val MIGRATION_7_8 = object : androidx.room.migration.Migration(7, 8) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE cards ADD COLUMN expiryTimestamp INTEGER")
                db.execSQL("ALTER TABLE identities ADD COLUMN expiryTimestamp INTEGER")
            }
        }

        val MIGRATION_8_9 = object : androidx.room.migration.Migration(8, 9) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS file_folders (
                        id TEXT PRIMARY KEY NOT NULL,
                        name TEXT NOT NULL,
                        parentFolderId TEXT,
                        createdAt INTEGER NOT NULL,
                        updatedAt INTEGER NOT NULL,
                        isFake INTEGER NOT NULL DEFAULT 0
                    )
                """.trimIndent())
                db.execSQL("ALTER TABLE files ADD COLUMN folderId TEXT")
            }
        }

        val MIGRATION_9_10 = object : androidx.room.migration.Migration(9, 10) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE notes ADD COLUMN isPinned INTEGER NOT NULL DEFAULT 0")
            }
        }

        /**
         * Creates an encrypted Room database using SQLCipher.
         * @param passphrase Derived from user master password + Keystore key.
         * @param dbName Name of the database file (allows switching for Fake Vault).
         */
        fun create(context: Context, passphrase: ByteArray, dbName: String = DB_NAME): VaultixDatabase {
            android.util.Log.d("VaultixDB", "Creating database '$dbName', passphrase length=${passphrase.size}")
            val factory = SupportFactory(passphrase)

            return Room.databaseBuilder(
                context.applicationContext,
                VaultixDatabase::class.java,
                dbName
            )
                .openHelperFactory(factory)
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_7_8, MIGRATION_8_9, MIGRATION_9_10)
                .fallbackToDestructiveMigration()
                .addCallback(object : RoomDatabase.Callback() {
                    override fun onCreate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                        super.onCreate(db)
                        android.util.Log.d("VaultixDB", "DATABASE CREATED FRESH - version=${db.version}")
                    }
                    override fun onOpen(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                        super.onOpen(db)
                        android.util.Log.d("VaultixDB", "DATABASE OPENED - version=${db.version}")
                        try {
                            val cursor = db.query("SELECT name FROM sqlite_master WHERE type='table'")
                            val tables = mutableListOf<String>()
                            while (cursor.moveToNext()) {
                                tables.add(cursor.getString(0))
                            }
                            cursor.close()
                            android.util.Log.d("VaultixDB", "Tables in DB: $tables")
                        } catch (e: Exception) {
                            android.util.Log.e("VaultixDB", "ERROR listing tables: ${e.message}", e)
                        }
                    }
                    override fun onDestructiveMigration(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                        super.onDestructiveMigration(db)
                        android.util.Log.w("VaultixDB", "DESTRUCTIVE MIGRATION executed - version=${db.version}")
                    }
                })
                .build()
        }
    }
}
