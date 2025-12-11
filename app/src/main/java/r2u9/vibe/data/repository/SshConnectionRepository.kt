package r2u9.vibe.data.repository

import kotlinx.coroutines.flow.Flow
import r2u9.vibe.data.dao.SshConnectionDao
import r2u9.vibe.data.model.SshConnection

class SshConnectionRepository(private val dao: SshConnectionDao) {
    val allConnections: Flow<List<SshConnection>> = dao.getAllConnections()

    suspend fun getAllConnectionsList(): List<SshConnection> = dao.getAllConnectionsList()

    suspend fun getConnectionById(id: Long): SshConnection? = dao.getConnectionById(id)

    suspend fun insertConnection(connection: SshConnection): Long = dao.insertConnection(connection)

    suspend fun updateConnection(connection: SshConnection) = dao.updateConnection(connection)

    suspend fun deleteConnection(connection: SshConnection) = dao.deleteConnection(connection)

    suspend fun deleteConnectionById(id: Long) = dao.deleteConnectionById(id)

    suspend fun updateLastConnected(id: Long) = dao.updateLastConnected(id, System.currentTimeMillis())
}
