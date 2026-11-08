package com.nanyang.virtualwin.model

import java.util.UUID
import com.google.gson.annotations.SerializedName

/**
 * 虚拟机架构类型
 */
enum class Arch { ARM_NATIVE, X86_EMU }

/**
 * BIOS 启动类型
 */
enum class BiosType { UEFI, LEGACY }

/**
 * 网络模式
 */
enum class NetworkMode { BRIDGE, NAT, NONE }

/**
 * 芯片优化目标
 */
enum class ChipOpt { DEFAULT, QCOMM, MEDIATEK, HUAWEI_KIRIN }

/**
 * 虚拟机磁盘格式（兼容 VMware VMDK / QCOW2 / RAW）
 */
enum class DiskFormat { VMDK, QCOW2, RAW }

/**
 * 虚拟机运行状态
 */
enum class VMStatus { STOPPED, STARTING, RUNNING, PAUSED, STOPPING, ERROR }

/**
 * 磁盘/镜像条目，支持本地路径或 OTG USB 设备
 */
data class DiskEntry(
    val path: String,
    val format: DiskFormat = DiskFormat.VMDK,
    val sizeGb: Int = 0,
    @SerializedName("is_iso")
    val isIso: Boolean = false,
    @SerializedName("label")
    val label: String? = null
)

/**
 * 虚拟机核心配置文件
 * JSON序列化到磁盘供 VMService 读取
 */
data class VMProfile(
    @SerializedName("id")
    val id: String = UUID.randomUUID().toString(),
    @SerializedName("name")
    var name: String = "Windows",
    /** 应用桌面图标（内嵌 base64 或 assets 路径） */
    @SerializedName("iconData")
    var iconData: String? = null,
    /** ARM原生=直接用SoC算力；X86=QEMU TCG模拟 */
    @SerializedName("arch")
    val arch: Arch = Arch.ARM_NATIVE,
    /** UEFI优先，兼容传统BIOS */
    @SerializedName("bios")
    val bios: BiosType = BiosType.UEFI,
    @SerializedName("tpm_enabled")
    val tpmEnabled: Boolean = true,
    /** CPU分配核心数 */
    @SerializedName("cpu_cores")
    val cpuCores: Int = 4,
    /** 内存（MB） */
    @SerializedName("ram_mb")
    val ramMb: Int = 4096,
    /** 虚拟化扩展是否启用（ARM SVE/KVM / x86 VT-x通过TCG） */
    @SerializedName("virt_enabled")
    val virtEnabled: Boolean = true,
    @SerializedName("disks")
    val disks: List<DiskEntry> = emptyList(),
    /** 网络配置 */
    @SerializedName("network_mode")
    val networkMode: NetworkMode = NetworkMode.BRIDGE,
    @SerializedName("bridge_iface")
    val bridgeIface: String = "wlan0",
    /** 芯片优化策略 */
    @SerializedName("chip_opt")
    val chipOpt: ChipOpt = ChipOpt.DEFAULT,
    /** 虚拟机根目录 */
    @SerializedName("vm_dir")
    var vmDir: String = "",
    @SerializedName("status")
    var status: VMStatus = VMStatus.STOPPED,
    @SerializedName("created_at")
    val createdAt: Long = System.currentTimeMillis(),
    @SerializedName("last_started_at")
    var lastStartedAt: Long = 0L
) {
    fun displayRam(): String = "${ramMb} MB"
    fun displayCpu(): String = "$cpuCores 核"
    fun displayBios(): String = when (bios) {
        BiosType.UEFI -> "UEFI"
        BiosType.LEGACY -> "BIOS"
    }
}
