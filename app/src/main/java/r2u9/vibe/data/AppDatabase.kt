package r2u9.vibe.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import r2u9.vibe.data.dao.SshConnectionDao
import r2u9.vibe.data.model.SshConnection

@Database(entities = [SshConnection::class], version = 1, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun sshConnectionDao(): SshConnectionDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "vibessh_database"
                ).build()
                INSTANCE = instance
                instance
            }
        }
    }
}
