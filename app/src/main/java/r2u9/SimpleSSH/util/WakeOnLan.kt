package r2u9.SimpleSSH.util

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress

object WakeOnLan {

    suspend fun sendMagicPacket(
        macAddress: String,
        broadcastAddress: String = "255.255.255.255",
        port: Int = 9
    ): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val macBytes = parseMacAddress(macAddress)
            val magicPacket = buildMagicPacket(macBytes)

            val address = InetAddress.getByName(broadcastAddress)
            val packet = DatagramPacket(magicPacket, magicPacket.size, address, port)

            DatagramSocket().use { socket ->
                socket.broadcast = true
                socket.send(packet)
            }

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun parseMacAddress(macAddress: String): ByteArray {
        val cleanMac = macAddress.replace(Regex("[:-]"), "")
        require(cleanMac.length == 12) { "Invalid MAC address format" }

        return cleanMac.chunked(2)
            .map { it.toInt(16).toByte() }
            .toByteArray()
    }

    private fun buildMagicPacket(macBytes: ByteArray): ByteArray {
        require(macBytes.size == 6) { "MAC address must be 6 bytes" }

        val packet = ByteArray(6 + 16 * 6)

        for (i in 0 until 6) {
            packet[i] = 0xFF.toByte()
        }

        for (i in 0 until 16) {
            System.arraycopy(macBytes, 0, packet, 6 + i * 6, 6)
        }

        return packet
    }

    fun isValidMacAddress(macAddress: String): Boolean {
        val patterns = listOf(
            Regex("^([0-9A-Fa-f]{2}[:-]){5}([0-9A-Fa-f]{2})$"),
            Regex("^[0-9A-Fa-f]{12}$")
        )
        return patterns.any { it.matches(macAddress) }
    }

    fun formatMacAddress(macAddress: String): String {
        val cleanMac = macAddress.replace(Regex("[:-]"), "").uppercase()
        return cleanMac.chunked(2).joinToString(":")
    }
}
