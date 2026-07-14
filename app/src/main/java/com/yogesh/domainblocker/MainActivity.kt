package com.yogesh.domainblocker

import android.R
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

class MainActivity : ComponentActivity() {

    private val vpnPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            startVpnService()
            BlockManager.setVpnActive(true)
        } else {
            BlockManager.setVpnActive(false)
        }
    }


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // database initialize
        BlockManager.initialize(applicationContext)

        setContent {
            // collect vpn ki current status
            val isVpnActive by BlockManager.isVpnActive.collectAsState()

            MaterialTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    BlockerScreen(
                        isVpnActive = isVpnActive,
                        onToggleVpn = { isActive ->
                            if (isActive) {
                                prepareAndStartVpn()
                            } else {
                                stopVpnService()
                                BlockManager.setVpnActive(false)
                            }
                        })
                }
            }
        }
    }

    private fun prepareAndStartVpn() {
        val intent = VpnService.prepare(this)
        if (intent != null)
            vpnPermissionLauncher.launch(intent)
        else {
            startVpnService()
            BlockManager.setVpnActive(true)
        }
    }

    private fun startVpnService() {
        val intent = Intent(this, LocalDnsVpnService::class.java)
        startService(intent)
    }

    private fun stopVpnService() {
        val intent = Intent(this, LocalDnsVpnService::class.java).apply {
            action = "ACTION_DISCONNECT"
        }
        startService(intent)
    }
}

@Composable
fun BlockerScreen(
    isVpnActive: Boolean,
    onToggleVpn: (Boolean) -> Unit
) {
    val blockedCount by BlockManager.blockedCount.collectAsState()
    val bundles by BlockManager.bundles.collectAsState()

    var showEditDialog by rememberSaveable { mutableStateOf(false) }
    var bundleNameToEdit by rememberSaveable { mutableStateOf<String?>(null) }

    Scaffold(
        floatingActionButton = {
            FloatingActionButton(
                onClick = {
                    bundleNameToEdit = null
                    showEditDialog = true
                },
                containerColor = MaterialTheme.colorScheme.primary
            ) {
                Icon(Icons.Filled.Add, contentDescription = "Add Bundle", tint = Color.White)
            }
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(24.dp))

            Text("DOMAIN BLOCKER", fontSize = 28.sp, fontWeight = FontWeight.Bold, fontStyle = FontStyle.Italic)

            Spacer(modifier = Modifier.height(32.dp))

            //power button ka background
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = if (isVpnActive) Color(0xFFE86E95) else MaterialTheme.colorScheme.surfaceVariant
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
                        fontSize = 18.sp, fontWeight = FontWeight.Medium,
                        color = if (isVpnActive) Color(0xFF2E7D32) else Color.Gray
                    )
                    Switch(checked = isVpnActive, onCheckedChange = onToggleVpn)
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            //blocked counter ka card
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text("Queries Blocked", fontSize = 14.sp, color = Color.Gray)
                    Text(
                        "$blockedCount",
                        fontSize = 48.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            Text(
                "Blocklists",
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
                    BundleItem(
                        bundle = bundle,
                        onEdit = {
                            bundleNameToEdit = it.name
                            showEditDialog = true
                        },
                        onDelete = { BlockManager.deleteBundle(it.name) }
                    )
                }
            }
        }
    }


    if (showEditDialog) {
        val bundleToEdit = bundles.find { it.name == bundleNameToEdit }
        BundleEditDialog(
            bundle = bundleToEdit,
            onDismiss = { showEditDialog = false },
            onSave = { oldName, newName, domains ->
                if (oldName == null) {
                    BlockManager.addBundle(newName, domains)
                } else {
                    BlockManager.updateBundle(oldName, newName, domains)
                }
                showEditDialog = false
            }
        )
    }
}

@Composable
fun BundleItem(
    bundle: DomainBundle,
    onEdit: (DomainBundle) -> Unit,
    onDelete: (DomainBundle) -> Unit
) {

    var isExpanded by rememberSaveable { mutableStateOf(false) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { isExpanded = !isExpanded }
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    bundle.name,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.weight(1f)
                )
                Checkbox(
                    checked = bundle.isEnabled,
                    onCheckedChange = { BlockManager.toggleBundle(bundle.name, it) }
                )
            }

            AnimatedVisibility(visible = isExpanded) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 16.dp, end = 16.dp, bottom = 12.dp)
                ) {
                    HorizontalDivider(modifier = Modifier.padding(bottom = 8.dp))

                    // edit and delete karne ka logic
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        IconButton(onClick = { onEdit(bundle) }) {
                            Icon(Icons.Default.Edit, contentDescription = "Edit", tint = Color.Gray)
                        }
                        IconButton(onClick = { onDelete(bundle) }) {
                            Icon(
                                Icons.Default.Delete,
                                contentDescription = "Delete",
                                tint = Color.Red
                            )
                        }
                    }

                    // domain list
                    bundle.domains.forEach { domain ->
                        Text(
                            "• $domain",
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

@Composable
fun BundleEditDialog(
    bundle: DomainBundle?,
    onDismiss: () -> Unit,
    onSave: (oldName: String?, newName: String, domains: List<String>) -> Unit
) {
    var name by rememberSaveable(bundle?.name) { mutableStateOf(bundle?.name ?: "") }
    var domainsText by rememberSaveable(bundle?.name) {
        mutableStateOf(
            bundle?.domains?.joinToString(
                "\n"
            ) ?: ""
        )
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (bundle == null) "Create New Bundle" else "Edit Bundle") },
        text = {
            Column {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Bundle Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = domainsText,
                    onValueChange = { domainsText = it },
                    label = { Text("Domains (One per line)") },
                    minLines = 5,
                    maxLines = 10,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val domainsList = domainsText.split("\n")
                        .map { it.trim().lowercase() }
                        .filter { it.isNotEmpty() }

                    if (name.isNotBlank() && domainsList.isNotEmpty()) {
                        onSave(bundle?.name, name, domainsList)
                    }
                }
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}