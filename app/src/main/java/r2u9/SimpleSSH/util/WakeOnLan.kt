package r2u9.SimpleSSH.util

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress

/**
 * Utility for sending Wake-on-LAN (WOL) magic packets.
 *
 * Wake-on-LAN is a networking standard that allows a computer to be turned on
 * remotely by sending a special "magic packet" over the network.
 */
object WakeOnLan {

    /**
     * Sends a Wake-on-LAN magic packet to wake a remote machine.
     *
     * @param macAddress The target device's MAC address (formats: AA:BB:CC:DD:EE:FF, AA-BB-CC-DD-EE-FF, or AABBCCDDEEFF)
     * @param broadcastAddress The broadcast address for the network (default: 255.255.255.255)
     * @param port The UDP port to send the packet to (default: 9, standard WOL port)
     * @return Result indicating success or failure with the exception
     */
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

        // Magic packet: 6 bytes of 0xFF followed by MAC address repeated 16 times
        val packet = ByteArray(6 + 16 * 6)

        // First 6 bytes are 0xFF
        for (i in 0 until 6) {
            packet[i] = 0xFF.toByte()
        }

        // Repeat MAC address 16 times
        for (i in 0 until 16) {
            System.arraycopy(macBytes, 0, packet, 6 + i * 6, 6)
        }

        return packet
    }

    /**
     * Validates a MAC address string.
     *
     * @param macAddress The MAC address to validate
     * @return true if the format is valid, false otherwise
     */
    fun isValidMacAddress(macAddress: String): Boolean {
        val patterns = listOf(
            Regex("^([0-9A-Fa-f]{2}[:-]){5}([0-9A-Fa-f]{2})$"),  // AA:BB:CC:DD:EE:FF or AA-BB-CC-DD-EE-FF
            Regex("^[0-9A-Fa-f]{12}$")  // AABBCCDDEEFF
        )
        return patterns.any { it.matches(macAddress) }
    }

    /**
     * Formats a MAC address to the canonical colon-separated uppercase format.
     *
     * @param macAddress The MAC address to format
     * @return The formatted MAC address (e.g., "AA:BB:CC:DD:EE:FF")
     */
    fun formatMacAddress(macAddress: String): String {
        val cleanMac = macAddress.replace(Regex("[:-]"), "").uppercase()
        return cleanMac.chunked(2).joinToString(":")
    }
}
