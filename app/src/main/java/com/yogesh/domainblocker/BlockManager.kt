package com.yogesh.domainblocker

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

// Data class to represent your bundles
data class DomainBundle(
    val name: String,
    val domains: List<String>,
    var isEnabled: Boolean = false
)

object BlockManager {
    // --- ADDED FOR VPN STATE ---
    private val _isVpnActive = MutableStateFlow(false)
    val isVpnActive: StateFlow<Boolean> = _isVpnActive.asStateFlow()

    fun setVpnActive(isActive: Boolean) {
        _isVpnActive.value = isActive
    }

    // 1. The Blocked Counter
    private val _blockedCount = MutableStateFlow(0)
    val blockedCount: StateFlow<Int> = _blockedCount.asStateFlow()

    fun incrementBlockCount() {
        _blockedCount.update { it + 1 }
    }

    // 2. The Domain Bundles
    private val _bundles = MutableStateFlow(
        listOf(
            DomainBundle("Ad Networks", listOf("ads.google.com", "doubleclick.net", "app-measurement.com"), true),
            DomainBundle("Social Media", listOf("facebook.com", "instagram.com", "tiktok.com", "graph.facebook.com"), false),
            DomainBundle("Malware & Tracking", listOf("telemetry.microsoft.com", "analytics.yahoo.com"), false)
        )
    )
    val bundles: StateFlow<List<DomainBundle>> = _bundles.asStateFlow()

    // 3. The Active Set (Used by the VPN for lightning-fast lookups)
    private val _activeBlockedDomains = MutableStateFlow<Set<String>>(emptySet())
    val activeBlockedDomains: StateFlow<Set<String>> = _activeBlockedDomains.asStateFlow()

    init {
        // Calculate initial active domains
        recalculateActiveDomains()
    }

    // Toggle a bundle on or off from the UI
    fun toggleBundle(bundleName: String, isEnabled: Boolean) {
        _bundles.update { currentBundles ->
            currentBundles.map {
                if (it.name == bundleName) it.copy(isEnabled = isEnabled) else it
            }
        }
        recalculateActiveDomains()
    }

    // Add a brand new bundle
    fun addBundle(name: String, domains: List<String>) {
        val newBundle = DomainBundle(name = name, domains = domains, isEnabled = true)
        _bundles.update { it + newBundle }
        recalculateActiveDomains()
    }

    // Update an existing bundle's name or domain list
    fun updateBundle(oldName: String, newName: String, newDomains: List<String>) {
        _bundles.update { currentBundles ->
            currentBundles.map {
                if (it.name == oldName) {
                    it.copy(name = newName, domains = newDomains)
                } else {
                    it
                }
            }
        }
        recalculateActiveDomains()
    }

    // Delete a bundle entirely
    fun deleteBundle(bundleName: String) {
        _bundles.update { currentBundles ->
            currentBundles.filter { it.name != bundleName }
        }
        recalculateActiveDomains()
    }

    private fun recalculateActiveDomains() {
        val newSet = mutableSetOf<String>()
        _bundles.value.forEach { bundle ->
            if (bundle.isEnabled) {
                newSet.addAll(bundle.domains)
            }
        }
        _activeBlockedDomains.value = newSet
    }
}