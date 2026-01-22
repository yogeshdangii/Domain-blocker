package com.yogesh.domainblocker

import android.content.Intent
import android.net.VpnService
import android.os.ParcelFileDescriptor
import android.util.Log
import java.io.FileInputStream
import java.io.FileOutputStream
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.nio.ByteBuffer
import java.util.concurrent.atomic.AtomicBoolean

class LocalDnsVpnService : VpnService() {

    private var vpnInterface: ParcelFileDescriptor? = null

    private var isRunning = AtomicBoolean(false)
    private var vpnThread: Thread? = null

    override fun onCreate() {
        super.onCreate()
        Log.d("VpnService", "VPN Service Created")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d("VpnService", "VPN Service Started! Configuring TUN...")

        if (!isRunning.get()) {
            isRunning.set(true)
            setupVpn()
        }
        return START_STICKY
    }

    private fun setupVpn() {
        try {
            val builder = Builder()
                .setSession("Domain Blocker")
                .addAddress("10.0.0.2", 24)
                .addDnsServer("10.0.0.2")
                .addRoute("10.0.0.0", 24)

            vpnInterface = builder.establish()

            vpnThread = Thread { packetLoop() }
            vpnThread?.start()

            Log.d("VpnService", "VPN Interface established successfully.")
        } catch (e: Exception) {
            Log.e("VpnService", "Error starting VPN: ${e.message}")
            stopSelf()
        }
    }

    private fun packetLoop() {
        val fileDescriptor = vpnInterface?.fileDescriptor ?: return

        val inputStream = FileInputStream(fileDescriptor)
        val outputStream = FileOutputStream(fileDescriptor)
        val packet = ByteArray(32767)

        Log.d("VpnService", "Packet loop started. Listening for DNS requests...")

        val forwardSocket = DatagramSocket()
        protect(forwardSocket)

        val realDnsIp = InetAddress.getByName("8.8.8.8")
        forwardSocket.soTimeout = 2000

        try {
            while (isRunning.get()) {
                val length = inputStream.read(packet)

                if (length > 0) {
                    if (packet[0] == 0x45.toByte()) {
                        val protocol = packet[9].toInt()

                        if (protocol == 17) {
                            val ipHeaderLen = 20
                            val destPort = ByteBuffer.wrap(packet, ipHeaderLen + 2, 2).short.toInt() and 0xFFFF

                            if (destPort == 53) {
                                val sourceIp = packet.copyOfRange(12, 16)
                                val sourcePort = ByteBuffer.wrap(packet, ipHeaderLen, 2).short

                                val dnsPayloadLen = (ByteBuffer.wrap(packet, ipHeaderLen + 4, 2).short.toInt() and 0xFFFF) - 8
                                val dnsPayload = packet.copyOfRange(ipHeaderLen + 8, ipHeaderLen + 8 + dnsPayloadLen)


                                val domainName = extractDomainName(dnsPayload)
                                Log.d("VpnService", "App requested domain: $domainName")

                                // --- THE FORWARDING STAGE ---
                                // Send the query to 8.8.8.8
                                val outPacket = DatagramPacket(dnsPayload, dnsPayload.size, realDnsIp, 53)
                                forwardSocket.send(outPacket)

                                // Wait for the real answer
                                val responseBuf = ByteArray(4096)
                                val inPacket = DatagramPacket(responseBuf, responseBuf.size)

                                try {
                                    forwardSocket.receive(inPacket)
                                    val dnsResponse = responseBuf.copyOfRange(0, inPacket.length)

                                    // Rebuild the IP and UDP headers and send back to the Android OS
                                    val finalResponse = buildIpv4UdpPacket(dnsResponse, sourceIp, sourcePort)
                                    outputStream.write(finalResponse)

                                } catch (e: Exception) {
                                    // Timeout occurred, just drop the packet
                                }
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            if (isRunning.get()) {
                Log.e("VpnService", "Error in packet loop: ${e.message}")
            }
        } finally {
            inputStream.close()
            outputStream.close()
            forwardSocket.close()
        }
    }


    private fun extractDomainName(dnsPayload: ByteArray): String {
        try {
            var i = 12 // The DNS Header is always 12 bytes. The domain starts at byte 13.
            val domain = StringBuilder()
            while (i < dnsPayload.size) {
                val len = dnsPayload[i].toInt() and 0xFF
                if (len == 0) break
                if ((len and 0xC0) == 0xC0) break
                if (domain.isNotEmpty()) domain.append(".")
                for (j in 1..len) {
                    domain.append(dnsPayload[i + j].toInt().toChar())
                }
                i += len + 1
            }
            return domain.toString()
        } catch (e: Exception) {
            return "unknown.domain"
        }
    }

    private fun buildIpv4UdpPacket(dnsResponse: ByteArray, destIp: ByteArray, destPort: Short): ByteArray {
        val ipHeaderLen = 20
        val udpHeaderLen = 8
        val totalLen = ipHeaderLen + udpHeaderLen + dnsResponse.size

        val packet = ByteArray(totalLen)
        val bb = ByteBuffer.wrap(packet)


        bb.put(0x45.toByte()) // version(4) + HL(5)
        bb.put(0.toByte()) // type of service
        bb.putShort(totalLen.toShort()) // total length
        bb.putShort(0.toShort()) // Identification no.
        bb.putShort(0.toShort()) // flaggs and fragmentation offset
        bb.put(64.toByte()) // ttl
        bb.put(17.toByte()) // Protocol (17 = UDP)
        bb.putShort(0.toShort()) // Checksum
        bb.put(byteArrayOf(10, 0, 0, 2)) // Source IP (Our VPN IP)
        bb.put(destIp) // Destination IP (The App)

            // making udp header
        bb.putShort(53.toShort()) // source port , dns
        bb.putShort(destPort) // destination port
        bb.putShort((udpHeaderLen + dnsResponse.size).toShort()) // udp legth
        bb.putShort(0.toShort()) // udp checksum

        // append dns payload
        bb.put(dnsResponse)

        // Calculate and Insert IP Checksum
        val checksum = calculateIpChecksum(packet, ipHeaderLen)
        bb.putShort(10, checksum.toShort())

        return packet
    }

    private fun calculateIpChecksum(packet: ByteArray, headerLen: Int): Int {
        var sum = 0
        var i = 0
        while (i < headerLen) {
            val word = ((packet[i].toInt() and 0xFF) shl 8) + (packet[i + 1].toInt() and 0xFF)
            sum += word
            i += 2
        }
        while (sum shr 16 != 0) {
            sum = (sum and 0xFFFF) + (sum shr 16)
        }
        return sum.inv() and 0xFFFF
    }

    override fun onDestroy() {
        Log.d("VpnService", "VPN Service Destroyed. Shutting down...")

        // stop loop
        isRunning.set(false)
        vpnThread?.interrupt()

        // Close the virtual network interface
        try {
            vpnInterface?.close()
        } catch (e: Exception) {
            Log.e("VpnService", "Error closing VPN interface: ${e.message}")
        }
        vpnInterface = null

        super.onDestroy()
    }
}