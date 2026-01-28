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
        Log.d("VpnService", "VPN Service Started")
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
                // IPv4 Configuration
                .addAddress("10.0.0.2", 24)
                .addDnsServer("10.0.0.1")
                .addRoute("10.0.0.0", 24)
                // IPv6 Blackhole Configuration (Prevents OS from leaking DNS via Cellular IPv6)
                .addAddress("fd00::2", 128)
                .addDnsServer("fd00::3")
                .addRoute("fd00::", 120)
                .setBlocking(true)

            vpnInterface = builder.establish()
            vpnThread = Thread { packetLoop() }
            vpnThread?.start()

            Log.d("VpnService", "VPN Interface established.")
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

        val forwardSocket = DatagramSocket()
        protect(forwardSocket) // Prevents the socket's own traffic from entering the VPN loop
        forwardSocket.soTimeout = 2000

        val realDnsIp = InetAddress.getByName("8.8.8.8")

        try {
            while (isRunning.get()) {
                val length = inputStream.read(packet)
                if (length > 0 && (packet[0].toInt() and 0xF0) == 0x40) { // IPv4 check
                    val protocol = packet[9].toInt()
                    if (protocol == 17) { // UDP
                        val ipHeaderLen = (packet[0].toInt() and 0x0F) * 4
                        val destPort = ByteBuffer.wrap(packet, ipHeaderLen + 2, 2).short.toInt() and 0xFFFF

                        if (destPort == 53) {
                            val sourceIp = packet.copyOfRange(12, 16)
                            val originalDestIp = packet.copyOfRange(16, 20)
                            val sourcePort = ByteBuffer.wrap(packet, ipHeaderLen, 2).short

                            val udpLen = ByteBuffer.wrap(packet, ipHeaderLen + 4, 2).short.toInt() and 0xFFFF
                            val dnsPayloadLen = udpLen - 8

                            if (dnsPayloadLen > 0 && ipHeaderLen + 8 + dnsPayloadLen <= length) {
                                val dnsPayload = packet.copyOfRange(ipHeaderLen + 8, ipHeaderLen + 8 + dnsPayloadLen)
                                val domainName = extractDomainName(dnsPayload)

                                if (isDomainBlocked(domainName)) {
                                    // 🛑 BLOCKED
                                    Log.d("VpnService", "🛡️ BLOCKED: $domainName")
                                    val spoofedDnsPayload = buildNxDomainResponse(dnsPayload)
                                    val finalResponse = buildIpv4UdpPacket(spoofedDnsPayload, sourceIp, sourcePort, originalDestIp)
                                    outputStream.write(finalResponse)

                                } else {
                                    // ✅ ALLOWED
                                    Log.d("VpnService", "DNS Request for: $domainName")
                                    val outPacket = DatagramPacket(dnsPayload, dnsPayload.size, realDnsIp, 53)
                                    forwardSocket.send(outPacket)

                                    val responseBuf = ByteArray(4096)
                                    val inPacket = DatagramPacket(responseBuf, responseBuf.size)

                                    try {
                                        forwardSocket.receive(inPacket)
                                        val dnsResponse = responseBuf.copyOfRange(0, inPacket.length)

                                        val finalResponse = buildIpv4UdpPacket(dnsResponse, sourceIp, sourcePort, originalDestIp)
                                        outputStream.write(finalResponse)
                                    } catch (e: Exception) {
                                        Log.w("VpnService", "DNS Timeout for $domainName")
                                    }
                                }
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("VpnService", "Loop error: ${e.message}")
        } finally {
            forwardSocket.close()
            try { inputStream.close() } catch (e: Exception) {}
            try { outputStream.close() } catch (e: Exception) {}
        }
    }

    private fun extractDomainName(dnsPayload: ByteArray): String {
        try {
            var i = 12 // Skip DNS header
            val domain = StringBuilder()
            while (i < dnsPayload.size) {
                val len = dnsPayload[i].toInt() and 0xFF
                if (len == 0) break
                if ((len and 0xC0) == 0xC0) {
                    domain.append("[ptr]")
                    break
                }
                if (domain.isNotEmpty()) domain.append(".")
                for (j in 1..len) {
                    domain.append(dnsPayload[i + j].toInt().toChar())
                }
                i += len + 1
            }
            return domain.toString()
        } catch (e: Exception) { return "unknown" }
    }

    private fun isDomainBlocked(domain: String): Boolean {
        // Get the live list from the UI bridge
        val liveBlockedDomains = BlockManager.activeBlockedDomains.value

        if (liveBlockedDomains.contains(domain)) {
            BlockManager.incrementBlockCount() // Tell the UI to increase the counter!
            return true
        }

        val parts = domain.split(".")
        if (parts.size >= 2) {
            val rootDomain = "${parts[parts.size - 2]}.${parts[parts.size - 1]}"
            if (liveBlockedDomains.contains(rootDomain)) {
                BlockManager.incrementBlockCount() // Tell the UI to increase the counter!
                return true
            }
        }
        return false
    }

    private fun buildNxDomainResponse(dnsQuery: ByteArray): ByteArray {
        val response = dnsQuery.copyOf()
        // DNS Header bytes 2 & 3 hold the flags.
        // 0x8183 = Standard Query Response, NXDOMAIN (Non-Existent Domain)
        if (response.size >= 4) {
            response[2] = 0x81.toByte()
            response[3] = 0x83.toByte()
        }
        return response
    }

    private fun buildIpv4UdpPacket(dnsResponse: ByteArray, destIp: ByteArray, destPort: Short, sourceIp: ByteArray): ByteArray {
        val ipHeaderLen = 20
        val udpHeaderLen = 8
        val totalLen = ipHeaderLen + udpHeaderLen + dnsResponse.size
        val packet = ByteArray(totalLen)
        val bb = ByteBuffer.wrap(packet)

        bb.put(0x45.toByte()) // IPv4 + IHL 5
        bb.put(0.toByte()) // TOS
        bb.putShort(totalLen.toShort())
        bb.putShort(0.toShort()) // ID
        bb.putShort(0.toShort()) // Flags
        bb.put(64.toByte()) // TTL
        bb.put(17.toByte()) // UDP
        bb.putShort(0.toShort()) // Checksum placeholder
        bb.put(sourceIp) // Source is what the app expects (10.0.0.1)
        bb.put(destIp) // Destination is the app's IP (10.0.0.2)

        bb.putShort(53.toShort()) // DNS Port
        bb.putShort(destPort)
        bb.putShort((udpHeaderLen + dnsResponse.size).toShort())
        bb.putShort(0.toShort()) // UDP Checksum placeholder

        bb.put(dnsResponse)

        val checksum = calculateIpChecksum(packet, ipHeaderLen)
        bb.putShort(10, checksum.toShort())
        return packet
    }

    private fun calculateIpChecksum(packet: ByteArray, headerLen: Int): Int {
        var sum = 0
        for (i in 0 until headerLen step 2) {
            sum += ((packet[i].toInt() and 0xFF) shl 8) + (packet[i + 1].toInt() and 0xFF)
        }
        while (sum shr 16 != 0) sum = (sum and 0xFFFF) + (sum shr 16)
        return sum.inv() and 0xFFFF
    }

    override fun onDestroy() {
        isRunning.set(false)
        vpnThread?.interrupt()
        try { vpnInterface?.close() } catch (e: Exception) {}
        super.onDestroy()
        Log.d("VpnService", "VPN Service Destroyed")
    }
}