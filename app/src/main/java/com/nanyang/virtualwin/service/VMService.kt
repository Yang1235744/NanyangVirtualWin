package com.nanyang.virtualwin.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.nanyang.virtualwin.R
import com.nanyang.virtualwin.model.Arch
import com.nanyang.virtualwin.model.BiosType
import com.nanyang.virtualwin.model.ChipOpt
import com.nanyang.virtualwin.model.VMProfile
import com.nanyang.virtualwin.model.VMStatus
import com.nanyang.virtualwin.repository.VMRepository
import com.nanyang.virtualwin.ui.activity.MainActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.max

/**
 * 虚拟机运行时前台 Service
 * 通过 ProcessBuilder 启动 QEMU 进程，监听生命周期并推送通知
 */
class VMService : Service() {

    companion object {
        private const val CHANNEL_ID = "nanyang_vm_runtime"
        private const val NOTIF_ID = 1001
        const val ACTION_START  = "com.nanyang.virtualwin.START_VM"
        const val ACTION_STOP   = "com.nanyang.virtualwin.STOP_VM"
        const val EXTRA_PROFILE_ID = "extra_profile_id"

        fun start(context: Context, profileId: String) {
            context.startForegroundService(Intent(context, VMService::class.java).apply {
                action = ACTION_START
                putExtra(EXTRA_PROFILE_ID, profileId)
            })
        }
        fun stop(context: Context) {
            context.startForegroundService(Intent(context, VMService::class.java).apply {
                action = ACTION_STOP
            })
        }
    }

    private val scope = CoroutineScope(Dispatchers.Default + Job())
    private var qemuProcess: Process? = null
    private lateinit var currentProfile: VMProfile
    private val notifier = USBOtgManager(this)

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                val profileId = intent.getStringExtra(EXTRA_PROFILE_ID)
                    ?: throw IllegalStateException("Missing EXTRA_PROFILE_ID")
                scope.launch {
                    currentProfile = VMRepository(this@VMService).loadById(profileId)
                        ?: throw IllegalStateException("Profile not found: $profileId")
                    startForeground(NOTIF_ID, buildNotification())
                    runVm(currentProfile)
                }
            }
            ACTION_STOP  -> stopSelf()
        }
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    /** 构建前���通知（显示电量/状态） */
    private fun buildNotification(): Notification {
        val pi = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("南阳虚拟Win — ${currentProfile.name}")
            .setContentText("运行中… 点击打开")
            .setSmallIcon(android.R.drawable.ic_menu_compass)
            .setContentIntent(pi)
            .setOngoing(true)
            .build()
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID, "虚拟机运行时", NotificationManager.IMPORTANCE_LOW
        )
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    /** 生成 QEMU 命令行参数并启动 */
    private suspend fun runVm(profile: VMProfile) {
        withContext(Dispatchers.IO) {
            // 根据芯片和品牌选择优化参数
            val chip = DeviceUtilsCompat.getChipBrand()
            val args = buildQemuArgs(profile, chip)
            notifyRunning(profile)

            try {
                val builder = ProcessBuilder(*args.toTypedArray())
                builder.redirectErrorStream(true)
                qemuProcess = builder.start()
                // 读取输出日志
                qemuProcess?.inputStream?.bufferedReader()?.useLines { lines ->
                    for (line in lines) {
                        if (!scope.isActive) break
                        // TODO: 收集 VNC/SPICE 帧数据推送到前端 SurfaceView
                    }
                }
            } catch (e: Exception) {
                notifyError(profile, e.message ?: "未知错误")
            } finally {
                qemuProcess = null
                notifyStopped(profile)
            }
        }
    }

    /**
     * 构建 QEMU 启动参数，按架构/BIOS/芯片差异化配置
     *
     * ARM原生模式 → 直接调用 arm-linux-user 或 KVM guest
     * x86模拟模式 → QEMU-system-x86_64 + TCG
     */
    private fun buildQemuArgs(p: VMProfile, chip: String): List<String> {
        val exe = when (p.arch) {
            Arch.ARM_NATIVE -> "qemu-system-arm"
            Arch.X86_EMU    -> "qemu-system-x86_64"
        }
        val cpuModel = when (chip) {
            "qcom"   -> if (p.chipOpt == ChipOpt.QCOMM) "cortex-a77" else "host"
            "mtk"    -> if (p.chipOpt == ChipOpt.MEDIATEK) "cortex-a76" else "host"
            "hisilicon" -> "cortex-a55"
            else     -> "host"
        }
        val accel = when (p.arch) {
            Arch.ARM_NATIVE -> "kvm"
            Arch.X86_EMU    -> "tcg"
        }

        return mutableListOf(
            exe,
            "-m", "${p.ramMb}",
            "-smp", "${p.cpuCores}",
            "-cpu", cpuModel,
            "-accel", accel,
            "-drive", "file=${p.vmDir}/disk.vmdk,format=vmdk,if=virtio",
            "-nic", "user",                          // NAT默认；桥接由上层配置
            "-serial", "null",                       // 禁用串口，改用 VNC
            "-vga", "virtio",
            "-display", "none",                      // GUI 由 SurfaceView接管
        ).also { list ->
            // UEFI vs 传统 BIOS固件路径
            val fwPath = if (p.bios == BiosType.UEFI) {
                "ovmf_aarch64.bin" // ARM64 UEFI
            } else {
                "bios-256k.bin"    // 传统 BIOS
            }
            if (p.arch == Arch.ARM_NATIVE) {
                list.add("-bios")
                list.add(FirmwareResolver.resolve(this, fwPath))
            } else {
                list.add("-L")
                list.add(filesDir.absolutePath + "/firmware")
            }
            // TPM 2.0
            if (p.tpmEnabled) {
                list.addAll(listOf(
                    "-chardev", "socket,id=tpm0,path=/tmp/tpm-${p.id}.sock",
                    "-tpmdev", "emulator,id=tpm0,chardev=tpm0",
                    "-device", "tpm-tis,tpm=tpm0"
                ))
            }
        }
    }

    private fun notifyRunning(p: VMProfile) {
        // TODO: 更新 VMRepository 状态为 RUNNING
    }
    private fun notifyStopped(p: VMProfile) {
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }
    private fun notifyError(p: VMProfile, msg: String) {
        // TODO: 更新 VMRepository 状态为 ERROR
    }

    override fun onDestroy() {
        (scope.coroutineContext[Job])?.cancel()
        notifier.destroy()
        qemuProcess?.destroy()
        super.onDestroy()
    }
}

/** 芯片品牌识别工具（独立文件以避免循环依赖） */
private object DeviceUtilsCompat {
    fun getChipBrand(): String {
        val hw = android.os.Build.HARDWARE.lowercase()
        return when {
            "qcom" in hw || "apq" in hw -> "qcom"
            "mtk" in hw || "mediatek" in hw -> "mtk"
            "hisilicon" in hw -> "hisilicon"
            else -> "other"
        }
    }
}

/** 固件解析器：从 assets/lib/firmware 查找 ROM 文件 */
private object FirmwareResolver {
    fun resolve(ctx: Context, name: String): String {
        val src = ctx.filesDir.resolve("firmware").resolve(name)
        if (src.exists()) return src.absolutePath
        // fallback：assets
        try {
            ctx.assets.open("firmware/$name").use { it.copyTo(src.outputStream()) }
            return src.absolutePath
        } catch (_: Exception) {}
        return ""
    }
}
