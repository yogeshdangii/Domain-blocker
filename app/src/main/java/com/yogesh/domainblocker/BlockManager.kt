package com.yogesh.domainblocker

import android.content.Context
import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

// to treat this class like sql table
@Entity(tableName = "bundles")
data class DomainBundle(
    @PrimaryKey val name: String,
    val domains: List<String>,
    var isEnabled: Boolean = false
)

// _ and bina _  => backing property bolte , good practice
// _vale actual variables , bina dash vale used to send data
// original variable remains stateful and can't be modified from outside of here normally

object BlockManager {
    // status of vpn
    private val _isVpnActive = MutableStateFlow(false)
    val isVpnActive: StateFlow<Boolean> = _isVpnActive.asStateFlow()

    fun setVpnActive(isActive: Boolean) {
        _isVpnActive.value = isActive
    }

    // blocked queries vala counter
    private val _blockedCount = MutableStateFlow(0)
    val blockedCount: StateFlow<Int> = _blockedCount.asStateFlow()

    fun incrementBlockCount() {
        _blockedCount.update { it + 1 }
    }

    // domaain bundles and data base
    private val _bundles = MutableStateFlow<List<DomainBundle>>(emptyList())
    val bundles: StateFlow<List<DomainBundle>> = _bundles.asStateFlow()

    private val _activeBlockedDomains = MutableStateFlow<Set<String>>(emptySet())
    val activeBlockedDomains: StateFlow<Set<String>> = _activeBlockedDomains.asStateFlow()

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var bundleDao: BundleDao? = null

    // when app starts
    fun initialize(context: Context) {
        if (bundleDao != null) return // already initialised
        val db = AppDatabase.getDatabase(context)
        bundleDao = db.bundleDao()

        // if db me change hota , this activated instant
        scope.launch {
            bundleDao!!.getAllBundles().collect { dbBundles ->
                if (dbBundles.isEmpty()) {
                    // first time opening app , apne default bundles load karo
                    DefaultBundles.getBundles().forEach { bundleDao!!.insertBundle(it) }
                } else {
                    _bundles.value = dbBundles
                    recalculateActiveDomains()
                }
            }
        }
    }

    fun toggleBundle(bundleName: String, isEnabled: Boolean) {
        scope.launch { bundleDao?.updateBundleStatus(bundleName, isEnabled) }
    }

    fun addBundle(name: String, domains: List<String>) {
        scope.launch { bundleDao?.insertBundle(DomainBundle(name, domains, true)) }
    }

    fun updateBundle(oldName: String, newName: String, newDomains: List<String>) {
        scope.launch {
            val oldBundle = _bundles.value.find { it.name == oldName }
            val isEnabled = oldBundle?.isEnabled ?: true
            if (oldName != newName) {
                bundleDao?.deleteBundleByName(oldName)
            }
            bundleDao?.insertBundle(DomainBundle(newName, newDomains, isEnabled))
        }
    }

    fun deleteBundle(bundleName: String) {
        scope.launch { bundleDao?.deleteBundleByName(bundleName) }
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