package com.yogesh.domainblocker

import android.content.Intent
import android.net.VpnService
import android.os.ParcelFileDescriptor
import android.util.Log
import java.io.FileInputStream
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

        // Only start the VPN setup if it isn't already running
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

        val packet = ByteArray(32767)
        Log.d("VpnService", "Packet loop started. Listening for DNS requests...")

        try {
            while (isRunning.get()) {
                val length = inputStream.read(packet)
                if (length > 0) {
                    Log.d("VpnService", "Caught a packet! Length: $length bytes")
                }
            }
        } catch (e: Exception) {
            if (isRunning.get()) {
                Log.e("VpnService", "Error reading packets: ${e.message}")
            }
        } finally {
            inputStream.close()
        }
    }

    override fun onDestroy() {
        Log.d("VpnService", "VPN Service Destroyed. Shutting down...")

        isRunning.set(false)
        vpnThread?.interrupt()

        try {
            vpnInterface?.close()
        } catch (e: Exception) {
            Log.e("VpnService", "Error closing VPN interface: ${e.message}")
        }
        vpnInterface = null
        super.onDestroy()
    }
}