package com.nanyang.virtualwin.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.nanyang.virtualwin.model.VMProfile
import com.nanyang.virtualwin.model.VMStatus
import com.nanyang.virtualwin.repository.VMRepository
import com.nanyang.virtualwin.service.VMService
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

    init {
        loadVms()
        // 启动时自动清理上次崩溃遗留的卡死状态（强制重置 ERROR）
        recoverStaleRunningVm()
    }

    fun loadVms() {
        viewModelScope.launch {
            _loading.value = true
            _vms.value = repository.loadAll().sortedByDescending { it.lastStartedAt }
            _loading.value = false
        }
    }

    /** 重启后扫描所有正在"运行中"状态的虚拟机，强制标记为 ERROR */
    private fun recoverStaleRunningVm() {
        viewModelScope.launch {
            val all = repository.loadAll()
            val stale = all.filter { it.status == VMStatus.RUNNING }
            if (stale.isNotEmpty()) {
                for (vm in stale) {
                    repository.save(vm.copy(status = VMStatus.ERROR))
                }
                loadVms()
            }
            // 同时停掉仍在运行的 Service（如果进程还挂着）
            stale.forEach { vm ->
                val updated = vm.copy(status = VMStatus.STOPPED, lastStartedAt = System.currentTimeMillis())
                viewModelScope.launch { repository.save(updated) }
            }
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

    /** 切换 VM 运行状态（点击播放/停止按钮触发） */
    fun toggleStart(profile: VMProfile) {
        when (profile.status) {
            VMStatus.STOPPED, VMStatus.ERROR -> VMService.start(getApplication(), profile.id)
            VMStatus.STARTING, VMStatus.PAUSED, VMStatus.STOPPING -> {} // 忽略，等待当前操作完成
            VMStatus.RUNNING -> VMService.stop(getApplication())
        }
    }

    /** 立即停止指定 VM（用于从卡片停止按钮调用） */
    fun stopVm(profile: VMProfile) {
        VMService.stop(getApplication())
    }

    /** 为 OTG USB 设备自动分配路径（传递给 VMService） */
    fun resolveUsbPath(usbDeviceId: Int): String? = null
}
