package r2u9.SimpleSSH.data.model

data class ActiveSession(
    val sessionId: String,
    val connection: SshConnection,
    val connectedAt: Long = System.currentTimeMillis(),
    var isConnected: Boolean = true
) {
    fun getConnectionDuration(): Long {
        return System.currentTimeMillis() - connectedAt
    }

    fun getFormattedDuration(): String {
        val duration = getConnectionDuration()
        val seconds = (duration / 1000) % 60
        val minutes = (duration / (1000 * 60)) % 60
        val hours = (duration / (1000 * 60 * 60))
        return String.format("%02d:%02d:%02d", hours, minutes, seconds)
    }
}
