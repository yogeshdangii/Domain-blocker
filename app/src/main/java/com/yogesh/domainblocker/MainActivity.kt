package com.yogesh.domainblocker

import android.app.Activity
import android.content.Intent
import android.net.VpnService
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// 1. Data schema for storing blocked domains by category
data class DomainBundle(
    val name: String,
    var isEnabled: Boolean,
    val domains: List<String>
)

class MainActivity : ComponentActivity() {

    // to check if vpn is running or not
    private val isVpnActive = mutableStateOf(false)

   // handling the permission to start vpn on android--
    private val vpnPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            // permission mil gai , start the service
            startVpnService()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // sample data for testing
        val initialBundles = listOf(
            DomainBundle("Social Media", false, listOf("facebook.com", "instagram.com", "tiktok.com")),
            DomainBundle("Ad Networks", true, listOf("doubleclick.net", "ads.google.com", "applovin.com")),
            DomainBundle("Analytics", false, listOf("flurry.com", "mixpanel.com", "crashlytics.com"))
        )

        setContent {
            MaterialTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    FirewallDashboard(
                        isActive = isVpnActive.value,
                        blockedCount = 0, // will implement this later using vpnservice
                        bundles = initialBundles,
                        onToggleVpn = { toggleVpn() }
                    )
                }
            }
        }
    }

    private fun toggleVpn() {
        if (isVpnActive.value) {
            // vpn is on, turn it off
            val intent = Intent(this, LocalDnsVpnService::class.java)
            stopService(intent)
            isVpnActive.value = false
        } else {
            // first check permission hai ki nahi
            val vpnIntent = VpnService.prepare(this)
            if (vpnIntent != null) {
                // We need permission. Launch the system dialog.
                vpnPermissionLauncher.launch(vpnIntent)
            } else {
                // We already have permission, start service
                startVpnService()
            }
        }
    }

    private fun startVpnService() {
        val intent = Intent(this, LocalDnsVpnService::class.java)
        startService(intent)
        isVpnActive.value = true
    }
}

@Composable
fun FirewallDashboard(
    isActive: Boolean,
    blockedCount: Int,
    bundles: List<DomainBundle>,
    onToggleVpn: () -> Unit
) {
    // State to hold the bundles so the UI updates when checkboxes are clicked
    var bundleState by remember { mutableStateOf(bundles) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(32.dp))

        // Power button ka ui
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = if (isActive) Color(0xFFE8F5E9) else Color(0xFFFFEBEE)
            ),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp)
                .clickable { onToggleVpn() }
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(
                        text = if (isActive) "Protection is ON" else "Protection is OFF",
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isActive) Color(0xFF2E7D32) else Color(0xFFC62828)
                    )
                    Text(
                        text = "Tap to toggle firewall",
                        fontSize = 14.sp,
                        color = Color.DarkGray
                    )
                }
                Switch(
                    checked = isActive,
                    onCheckedChange = { onToggleVpn() },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.White,
                        checkedTrackColor = Color(0xFF4CAF50),
                        uncheckedThumbColor = Color.White,
                        uncheckedTrackColor = Color(0xFFEF5350)
                    )
                )
            }
        }


        Spacer(modifier = Modifier.height(24.dp))

        // 3. The Blocked Counter
        Text(
            text = "$blockedCount Queries Blocked",
            fontSize = 20.sp,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onBackground
        )

        Spacer(modifier = Modifier.height(32.dp))
        HorizontalDivider()
        Spacer(modifier = Modifier.height(16.dp))

        // 4. The Bundle List
        Text(
            text = "Block Rules",
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.align(Alignment.Start)
        )
        Spacer(modifier = Modifier.height(16.dp))

        LazyColumn(modifier = Modifier.fillMaxWidth()) {
            items(bundleState) { bundle ->
                BundleItem(
                    bundle = bundle,
                    onBundleToggled = { isChecked ->
                        // Update the specific bundle's state
                        bundleState = bundleState.map {
                            if (it.name == bundle.name) it.copy(isEnabled = isChecked) else it
                        }
                    }
                )
                Spacer(modifier = Modifier.height(12.dp))
            }
        }
    }
}

@Composable
fun BundleItem(bundle: DomainBundle, onBundleToggled: (Boolean) -> Unit) {
    // Tracks if this specific bundle is expanded to show its domains
    var isExpanded by remember { mutableStateOf(false) }

    Card(
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            // Bundle Header (Always visible)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { isExpanded = !isExpanded } // Tap to expand/collapse
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = bundle.name,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Switch(
                    checked = bundle.isEnabled,
                    onCheckedChange = { onBundleToggled(it) }
                )
            }

            // Expanded Domain List
            if (isExpanded) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color.Black.copy(alpha = 0.05f))
                        .padding(16.dp)
                ) {
                    Text(
                        text = "Domains in this bundle:",
                        fontSize = 12.sp,
                        color = Color.Gray,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    bundle.domains.forEach { domain ->
                        Text(
                            text = domain,
                            fontSize = 14.sp,
                            modifier = Modifier.padding(vertical = 4.dp)
                        )
                    }
                }
            }
        }
    }
}