package com.yogesh.domainblocker

import android.content.Intent
import android.net.VpnService
import android.util.Log

class LocalDnsVpnService : VpnService() {

    override fun onCreate() {
        super.onCreate()
        Log.d("VpnService", "VPN Service Created!")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d("VpnService", "VPN Service Started! Waiting for TUN configuration.")

        // i will set up TUN interface here


        return START_STICKY
    }

    override fun onDestroy() {
        Log.d("VpnService", "VPN Service Destroyed!")

        // Tun interface yaha khatam hoga
        super.onDestroy()
    }
}