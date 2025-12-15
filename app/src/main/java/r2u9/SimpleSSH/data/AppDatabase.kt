package r2u9.SimpleSSH.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import r2u9.SimpleSSH.data.dao.SshConnectionDao
import r2u9.SimpleSSH.data.model.SshConnection

@Database(entities = [SshConnection::class], version = 2, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun sshConnectionDao(): SshConnectionDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE ssh_connections ADD COLUMN wolEnabled INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE ssh_connections ADD COLUMN wolMacAddress TEXT")
                db.execSQL("ALTER TABLE ssh_connections ADD COLUMN wolBroadcastAddress TEXT")
                db.execSQL("ALTER TABLE ssh_connections ADD COLUMN wolPort INTEGER NOT NULL DEFAULT 9")
            }
        }

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "vibessh_database"
                )
                    .addMigrations(MIGRATION_1_2)
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
