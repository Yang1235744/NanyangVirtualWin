package com.nanyang.virtualwin.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.nanyang.virtualwin.model.VMProfile
import com.nanyang.virtualwin.repository.VMRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class MainViewModel(app: Application) : AndroidViewModel(app) {

    private val repository = VMRepository(app)

    private val _vms = MutableStateFlow<List<VMProfile>>(emptyList())
    val vms: StateFlow<List<VMProfile>> = _vms.asStateFlow()

    private val _loading = MutableStateFlow(false)
    val loading: StateFlow<Boolean> = _loading.asStateFlow()

    init { loadVms() }

    fun loadVms() {
        viewModelScope.launch {
            _loading.value = true
            _vms.value = repository.loadAll().sortedByDescending { it.lastStartedAt }
            _loading.value = false
        }
    }

    suspend fun addVm(profile: VMProfile) {
        repository.save(profile)
        loadVms()
    }

    suspend fun deleteVm(id: String) {
        repository.delete(id)
        loadVms()
    }

    fun toggleStart(profile: VMProfile) {
        if (profile.status.name == "STOPPED" || profile.status.name == "ERROR") {
            com.nanyang.virtualwin.service.VMService.start(getApplication(), profile.id)
        } else {
            com.nanyang.virtualwin.service.VMService.stop(getApplication())
        }
    }

    /** 为 OTG USB 设备自动分配路径（传递给 VMService） */
    fun resolveUsbPath(usbDeviceId: Int): String? {
        return null  // TODO: 通过 USBOtgManager 查询
    }
}
