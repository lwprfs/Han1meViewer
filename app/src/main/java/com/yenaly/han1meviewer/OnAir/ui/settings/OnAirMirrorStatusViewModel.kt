package com.yenaly.han1meviewer.OnAir.ui.settings

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.yenaly.han1meviewer.OnAir.common.OnAirConstants
import com.yenaly.han1meviewer.OnAir.common.OnAirSitePreferences
import com.yenaly.han1meviewer.OnAir.data.model.OnAirMirrorStatus
import com.yenaly.han1meviewer.OnAir.data.remote.OnAirMirrorStatusRepo
import com.yenaly.han1meviewer.OnAir.data.remote.OnAirNetwork
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

class OnAirMirrorStatusViewModel : ViewModel() {

    val statuses = mutableStateMapOf<String, OnAirMirrorStatus>()

    var checkingAll by mutableStateOf(false)
        private set

    private var bulkJob: Job? = null

    val selectedHost: String
        get() = OnAirSitePreferences.currentHostname()

    fun checkOne(host: String) {
        if (statuses[host] is OnAirMirrorStatus.Checking) return
        viewModelScope.launch {
            statuses[host] = OnAirMirrorStatus.Checking(host)
            statuses[host] = OnAirMirrorStatusRepo.check(host)
        }
    }

    fun checkAll() {
        if (checkingAll) return
        bulkJob?.cancel()
        checkingAll = true
        bulkJob = viewModelScope.launch {
            try {
                OnAirMirrorStatusRepo.checkAll(OnAirConstants.ONAIR_HOSTNAME.toList()) { result ->
                    statuses[result.host] = result
                }
            } finally {
                checkingAll = false
            }
        }
    }

    fun switchTo(host: String) {
        if (host == OnAirSitePreferences.currentHostname()) return
        val idx = OnAirConstants.ONAIR_HOSTNAME.indexOfFirst {
            it.equals(host, ignoreCase = true)
        }
        if (idx < 0) return
        OnAirSitePreferences.setMirrorIndex(idx)
        OnAirNetwork.rebuildNetwork()
        statuses.clear()
        checkAll()
    }

    override fun onCleared() {
        super.onCleared()
        bulkJob?.cancel()
    }
}
