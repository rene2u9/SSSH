package r2u9.vibe.data.dao

import androidx.room.*
import kotlinx.coroutines.flow.Flow
import r2u9.vibe.data.model.SshConnection

@Dao
interface SshConnectionDao {
    @Query("SELECT * FROM ssh_connections ORDER BY lastConnectedAt DESC, createdAt DESC")
    fun getAllConnections(): Flow<List<SshConnection>>

    @Query("SELECT * FROM ssh_connections ORDER BY lastConnectedAt DESC, createdAt DESC")
    suspend fun getAllConnectionsList(): List<SshConnection>

    @Query("SELECT * FROM ssh_connections WHERE id = :id")
    suspend fun getConnectionById(id: Long): SshConnection?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertConnection(connection: SshConnection): Long

    @Update
    suspend fun updateConnection(connection: SshConnection)

    @Delete
    suspend fun deleteConnection(connection: SshConnection)

    @Query("DELETE FROM ssh_connections WHERE id = :id")
    suspend fun deleteConnectionById(id: Long)

    @Query("UPDATE ssh_connections SET lastConnectedAt = :timestamp WHERE id = :id")
    suspend fun updateLastConnected(id: Long, timestamp: Long)
}
