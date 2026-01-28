package com.yogesh.domainblocker

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

data class DomainBundle(
    val name: String,
    val domains: List<String>,
    var isEnabled: Boolean = false
)

object BlockManager {
    private val _blockedCount = MutableStateFlow(0)
    val blockedCount: StateFlow<Int> = _blockedCount.asStateFlow()

    fun incrementBlockCount() {
        _blockedCount.update { it + 1 }
    }

    private val _bundles = MutableStateFlow(
        listOf(
            DomainBundle("Ad Networks", listOf("ads.google.com", "doubleclick.net", "app-measurement.com")),
            DomainBundle("Social Media", listOf("facebook.com", "instagram.com", "tiktok.com", "graph.facebook.com")),
            DomainBundle("Malware & Tracking", listOf("telemetry.microsoft.com", "analytics.yahoo.com"))
        )
    )
    val bundles: StateFlow<List<DomainBundle>> = _bundles.asStateFlow()

    private val _activeBlockedDomains = MutableStateFlow<Set<String>>(emptySet())
    val activeBlockedDomains: StateFlow<Set<String>> = _activeBlockedDomains.asStateFlow()

    fun toggleBundle(bundleName: String, isEnabled: Boolean) {
        _bundles.update { currentBundles ->
            currentBundles.map {
                if (it.name == bundleName) it.copy(isEnabled = isEnabled) else it
            }
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