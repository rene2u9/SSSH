package r2u9.SimpleSSH.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import r2u9.SimpleSSH.data.AppDatabase
import r2u9.SimpleSSH.data.model.ActiveSession
import r2u9.SimpleSSH.data.model.SshConnection
import r2u9.SimpleSSH.data.repository.SshConnectionRepository

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val repository: SshConnectionRepository

    private val _connections = MutableStateFlow<List<SshConnection>>(emptyList())
    val connections: StateFlow<List<SshConnection>> = _connections.asStateFlow()

    private val _activeSessions = MutableStateFlow<List<ActiveSession>>(emptyList())
    val activeSessions: StateFlow<List<ActiveSession>> = _activeSessions.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    init {
        val database = AppDatabase.getDatabase(application)
        repository = SshConnectionRepository(database.sshConnectionDao())

        viewModelScope.launch {
            repository.allConnections.collect { list ->
                _connections.value = list
            }
        }
    }

    fun addConnection(connection: SshConnection) {
        viewModelScope.launch {
            repository.insertConnection(connection)
        }
    }

    fun updateConnection(connection: SshConnection) {
        viewModelScope.launch {
            repository.updateConnection(connection)
        }
    }

    fun deleteConnection(connection: SshConnection) {
        viewModelScope.launch {
            repository.deleteConnection(connection)
        }
    }

    fun updateLastConnected(connectionId: Long) {
        viewModelScope.launch {
            repository.updateLastConnected(connectionId)
        }
    }

    suspend fun getAllConnectionsForExport(): List<SshConnection> {
        return repository.getAllConnectionsList()
    }

    fun importConnections(connections: List<SshConnection>) {
        viewModelScope.launch {
            connections.forEach { connection ->
                repository.insertConnection(connection)
            }
        }
    }

    fun updateActiveSessions(sessions: List<ActiveSession>) {
        _activeSessions.value = sessions
    }
}
