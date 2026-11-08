package com.nanyang.virtualwin.viewmodel

import android.app.Application
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.nanyang.virtualwin.model.Arch
import com.nanyang.virtualwin.model.BiosType
import com.nanyang.virtualwin.model.ChipOpt
import com.nanyang.virtualwin.model.DiskEntry
import com.nanyang.virtualwin.model.NetworkMode
import com.nanyang.virtualwin.model.VMProfile
import com.nanyang.virtualwin.repository.VMRepository
import com.nanyang.virtualwin.util.DeviceUtils
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class NewVMViewModel(app: Application) : AndroidViewModel(app) {

    private val repository = VMRepository(app)

    // 表单状态
    private val _name      = MutableStateFlow("Windows")
    private val _arch      = MutableStateFlow(Arch.ARM_NATIVE)
    private val _bios      = MutableStateFlow(BiosType.UEFI)
    private val _tpmEnabled= MutableStateFlow(true)
    private val _cpuCores  = MutableStateFlow(DeviceUtils.availableCores().coerceAtLeast(2))
    private val _ramMb     = MutableStateFlow(if (DeviceUtils.totalMemoryMb(app) > 6000) 4096 else 2048)
    private val _diskGb    = MutableStateFlow(32)
    private val _networkMode = MutableStateFlow(NetworkMode.BRIDGE)
    private val _isoPath   = MutableStateFlow("")
    private val _iconBytes = MutableStateFlow<ByteArray?>(null)

    /** 图标预览位图（自动从 bytes 解码） */
    private val _iconBitmap = MutableStateFlow<Bitmap?>(null)
    val iconBitmap: StateFlow<Bitmap?> get() = _iconBitmap

    init {
        viewModelScope.launch {
            _iconBytes.collect { bytes ->
                _iconBitmap.value = bytes?.let { BitmapFactory.decodeByteArray(it, 0, it.size) }
            }
        }
    }

    val isSuccess = MutableStateFlow(false)

    fun setName(n: String)              { _name.value = n }
    fun setArch(a: Arch)                 { _arch.value = a }
    fun setBios(b: BiosType)             { _bios.value = b }
    fun toggleTpm()                      { _tpmEnabled.value = !_tpmEnabled.value }
    fun setCpu(n: Int)                   { _cpuCores.value = n.coerceIn(1, DeviceUtils.availableCores()) }
    fun setRam(mb: Int)                  { _ramMb.value = mb }
    fun setDiskGb(gb: Int)               { _diskGb.value = gb }
    fun setNetwork(m: NetworkMode)       { _networkMode.value = m }
    fun setIso(path: String)             { _isoPath.value = path }
    fun setIconUri(uri: Uri) {
        viewModelScope.launch {
            _iconBytes.value = getApplication<Application>().contentResolver
                .openInputStream(uri)?.readBytes()
        }
    }

    suspend fun createProfile(): Boolean = try {
        val profile = VMProfile(
            name          = _name.value,
            arch          = _arch.value,
            bios          = _bios.value,
            tpmEnabled    = _tpmEnabled.value,
            cpuCores      = _cpuCores.value,
            ramMb         = _ramMb.value,
            networkMode   = _networkMode.value,
            chipOpt       = resolveChipOpt(),
            disks         = listOf(
                DiskEntry(
                    path    = "",
                    format  = com.nanyang.virtualwin.model.DiskFormat.VMDK,
                    sizeGb  = _diskGb.value,
                    label   = "System disk (${_diskGb.value}GB)"
                )
            )
        )
        if (_isoPath.value.isNotEmpty()) {
            val newDisks = profile.disks + DiskEntry(
                path  = _isoPath.value,
                isIso = true,
                label = "Boot ISO"
            )
            repository.save(profile.copy(disks = newDisks))
        } else {
            repository.save(profile)
        }
        true
    } catch (e: Exception) {
        e.printStackTrace()
        false
    }

    private fun resolveChipOpt(): ChipOpt = when (DeviceUtils.getChipBrand()) {
        "qcom"     -> ChipOpt.QCOMM
        "mtk"      -> ChipOpt.MEDIATEK
        "hisilicon"-> ChipOpt.HUAWEI_KIRIN
        else       -> ChipOpt.DEFAULT
    }
}
