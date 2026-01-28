package com.yogesh.domainblocker

import android.app.Activity
import android.content.Intent
import android.net.VpnService
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

class MainActivity : ComponentActivity() {

    // Launcher to ask for Android VPN permission if not already granted
    private val vpnPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            startVpnService()
        } else {
            // Permission denied by user
            isVpnActive = false
        }
    }

    // Keep track of the switch state
    private var isVpnActive by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    BlockerScreen(
                        isVpnActive = isVpnActive,
                        onToggleVpn = { isActive ->
                            isVpnActive = isActive
                            if (isActive) {
                                prepareAndStartVpn()
                            } else {
                                stopVpnService()
                            }
                        }
                    )
                }
            }
        }
    }

    private fun prepareAndStartVpn() {
        val intent = VpnService.prepare(this)
        if (intent != null) {
            // Permission is needed, launch the system dialog
            vpnPermissionLauncher.launch(intent)
        } else {
            // Permission already granted, start immediately
            startVpnService()
        }
    }

    private fun startVpnService() {
        val intent = Intent(this, LocalDnsVpnService::class.java)
        startService(intent)
    }

    private fun stopVpnService() {
        val intent = Intent(this, LocalDnsVpnService::class.java)
        stopService(intent)
    }
}

@Composable
fun BlockerScreen(
    isVpnActive: Boolean,
    onToggleVpn: (Boolean) -> Unit
) {
    // Collect live data from the BlockManager singleton
    val blockedCount by BlockManager.blockedCount.collectAsState()
    val bundles by BlockManager.bundles.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(24.dp))

        // 1. The Header
        Text(
            text = "Local Ad Blocker",
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(32.dp))

        // 2. The Power Toggle Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = if (isVpnActive) Color(0xFFE8F5E9) else MaterialTheme.colorScheme.surfaceVariant
            )
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (isVpnActive) "Protection is ON" else "Protection is OFF",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Medium,
                    color = if (isVpnActive) Color(0xFF2E7D32) else Color.Gray
                )
                Switch(
                    checked = isVpnActive,
                    onCheckedChange = onToggleVpn
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // 3. The Live Counter Card
        Card(
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(text = "Queries Blocked", fontSize = 14.sp, color = Color.Gray)
                Text(
                    text = "$blockedCount",
                    fontSize = 48.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }

        Spacer(modifier = Modifier.height(32.dp))

        // 4. The Blocklist Bundles
        Text(
            text = "Blocklists",
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.align(Alignment.Start)
        )

        Spacer(modifier = Modifier.height(8.dp))

        LazyColumn(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(bundles) { bundle ->
                BundleItem(bundle = bundle)
            }
        }
    }
}

@Composable
fun BundleItem(bundle: DomainBundle) {
    // Local state to control the dropdown expansion
    var isExpanded by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth()
        ) {
            // Visible Row (Bundle Name and Checkbox)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { isExpanded = !isExpanded } // Tap anywhere to expand
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = bundle.name,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Medium
                )
                Checkbox(
                    checked = bundle.isEnabled,
                    onCheckedChange = { isChecked ->
                        BlockManager.toggleBundle(bundle.name, isChecked)
                    }
                )
            }

            // Expandable List of Domains
            AnimatedVisibility(visible = isExpanded) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 16.dp, end = 16.dp, bottom = 12.dp)
                ) {
                    Divider(modifier = Modifier.padding(bottom = 8.dp))
                    bundle.domains.forEach { domain ->
                        Text(
                            text = "• $domain",
                            fontSize = 14.sp,
                            color = Color.Gray,
                            modifier = Modifier.padding(vertical = 2.dp)
                        )
                    }
                }
            }
        }
    }
}