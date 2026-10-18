package com.lovelev.mediadock.spike

import android.content.Context
import android.net.wifi.WifiManager
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.hierynomus.smbj.SMBClient
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.SocketTimeoutException

/**
 * S3 spike:
 *  - raw SSDP discovery (the basis of the in-house DLNA controller)
 *  - SMBJ client construction
 *
 * jUPnP 3.0.5 was evaluated and rejected: its Android module calls Jetty classes
 * that are not declared as dependencies (and the compatible Jetty line is old).
 */
@RunWith(AndroidJUnit4::class)
class NetworkClientsSpikeTest {

    @Test
    fun rawSsdpDiscovery() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val wifi = context.getSystemService(Context.WIFI_SERVICE) as? WifiManager
        val multicastLock = wifi?.createMulticastLock("media-dock-ssdp")?.apply {
            setReferenceCounted(false)
            acquire()
        }
        val responses = mutableListOf<String>()
        val socket = DatagramSocket()
        socket.soTimeout = 3_000
        try {
            val message = buildString {
                append("M-SEARCH * HTTP/1.1\r\n")
                append("HOST: 239.255.255.250:1900\r\n")
                append("MAN: \"ssdp:discover\"\r\n")
                append("MX: 2\r\n")
                append("ST: ssdp:all\r\n")
                append("\r\n")
            }.toByteArray()
            val address = InetAddress.getByName("239.255.255.250")
            repeat(3) {
                socket.send(DatagramPacket(message, message.size, address, 1900))
                Thread.sleep(300)
            }

            val deadline = System.currentTimeMillis() + 6_000
            val buffer = ByteArray(4096)
            while (System.currentTimeMillis() < deadline) {
                val packet = DatagramPacket(buffer, buffer.size)
                try {
                    socket.receive(packet)
                } catch (_: SocketTimeoutException) {
                    break
                }
                val text = String(packet.data, 0, packet.length)
                val location = text.lineSequence()
                    .firstOrNull { it.startsWith("LOCATION:", ignoreCase = true) }
                    ?.substringAfter(':')
                    ?.trim()
                val server = text.lineSequence()
                    .firstOrNull { it.startsWith("SERVER:", ignoreCase = true) }
                    ?.substringAfter(':')
                    ?.trim()
                responses += (location ?: server ?: text.take(80))
            }
            Log.i(TAG, "SSDP responses=${responses.size}")
            responses.distinct().forEach { Log.i(TAG, "SSDP device: $it") }
            assertTrue("SSDP search completed without exception", true)
        } finally {
            socket.close()
            multicastLock?.release()
        }
    }

    @Test
    fun smbClientConstructs() {
        val client = SMBClient()
        assertNotNull(client)
        Log.i(TAG, "SMBJ client constructed; live share test requires smbHost/share/user/password")
    }

    private companion object {
        const val TAG = "S3Clients"
    }
}
