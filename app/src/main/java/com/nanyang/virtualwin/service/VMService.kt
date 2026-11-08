package com.nanyang.virtualwin.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
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
 *
 * 修复内容：
 *  - QEMU 不存在时不再抛出未捕获异常导致进程崩掉，改为显示错误通知并重置状态
 *  - notifyRunning / notifyError / notifyStopped 现在会实际写回 Repository
 *  - 用 companion 级别标志防止重复启动同一个 profile 的并发问题
 */
class VMService : Service() {

    companion object {
        private const val CHANNEL_ID = "nanyang_vm_runtime"
        private const val NOTIF_ID   = 1001
        private const val ERROR_NOTIF_ID = 1002
        const val ACTION_START  = "com.nanyang.virtualwin.START_VM"
        const val ACTION_STOP   = "com.nanyang.virtualwin.STOP_VM"
        const val EXTRA_PROFILE_ID = "extra_profile_id"

        // 全局启动锁：防止同一 profile 被并发启动导致状态错乱
        private var isRunning = false

        fun start(context: Context, profileId: String) {
            if (isRunning) return  // 已有 VM 在跑，忽略重复请求
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
        /** 强制停止当前运行（用于系统级恢复） */
        fun forceStop(context: Context) {
            context.stopService(Intent(context, VMService::class.java))
            isRunning = false
        }
    }

    private val scope = CoroutineScope(Dispatchers.Default + Job())
    private var qemuProcess: Process? = null
    private lateinit var currentProfile: VMProfile
    private val notifier = USBOtgManager(this)
    private lateinit var repo: VMRepository

    override fun onCreate() {
        super.onCreate()
        repo = VMRepository(this)
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                val profileId = intent.getStringExtra(EXTRA_PROFILE_ID)
                    ?: throw IllegalStateException("Missing EXTRA_PROFILE_ID")
                scope.launch {
                    try {
                        currentProfile = repo.loadById(profileId)
                            ?: throw IllegalStateException("Profile not found: $profileId")
                        startForeground(NOTIF_ID, buildNotification())
                        runVm(currentProfile)
                    } catch (e: Exception) {
                        notifyError(currentProfile, e.message ?: "未知错误")
                    }
                }
            }
            ACTION_STOP -> {
                isRunning = false
                stopSelf()
            }
        }
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun buildNotification(): Notification {
        val pi = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("南阳虚拟Win — ${currentProfile.name}")
            .setContentText("正在初始化…")
            .setSmallIcon(android.R.drawable.ic_menu_compass)
            .setContentIntent(pi)
            .setOngoing(true)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID, "虚拟机运行时", NotificationManager.IMPORTANCE_LOW
            ).apply { description = "虚拟机运行状态通知" }
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    /** ── 核心：启动虚拟机 ─────────────────────────────── */
    private suspend fun runVm(profile: VMProfile) {
        // 标记已启动（必须在上层 try/catch 之外设置，保证 finally 能读到）
        isRunning = true
        notifyRunning(profile)

        withContext(Dispatchers.IO) {
            val chip = DeviceUtilsCompat.getChipBrand()
            val args = buildQemuArgs(profile, chip)

            // ── 检查 QEMU 二进制是否存在 ──────────────────
            val exeName = args.firstOrNull() ?: "qemu-system-arm"
            if (!isQemuAvailable(exeName)) {
                val msg = "未找到 $exeName\n\n请安装 QEMU ARM/x86 模拟器后再试，或使用预编译版本。"
                notifyError(profile, msg)
                // 显示错误通知让用户知道原因
                showErrorNotification(profile, msg)
                return@withContext
            }

            try {
                val builder = ProcessBuilder(*args.toTypedArray())
                    .directory(File(profile.vmDir))
                    .redirectErrorStream(true)
                qemuProcess = builder.start()
                showRunningNotification(profile)
                qemuProcess?.inputStream?.bufferedReader()?.useLines { lines ->
                    for (line in lines) {
                        if (!scope.isActive || !isRunning) break
                    }
                }
                if (scope.isActive && isRunning) notifyStopped(profile)
            } catch (e: Exception) {
                notifyError(profile, "${e.message ?: "QEMU 启动失败"}")
                showErrorNotification(profile, e.message ?: "未知错误")
            } finally {
                qemuProcess?.destroy()
                qemuProcess = null
                if (isRunning) notifyStopped(profile)
            }
        }
    }

    /** 构建 QEMU 命令行参数 */
    private fun buildQemuArgs(p: VMProfile, chip: String): List<String> {
        val exe = when (p.arch) {
            Arch.ARM_NATIVE -> "qemu-system-arm"
            Arch.X86_EMU    -> "qemu-system-x86_64"
        }
        val cpuModel = when (chip) {
            "qcom"      -> if (p.chipOpt == ChipOpt.QCOMM) "cortex-a77" else "host"
            "mtk"       -> if (p.chipOpt == ChipOpt.MEDIATEK) "cortex-a76" else "host"
            "hisilicon" -> "cortex-a55"
            else        -> "host"
        }
        val accel = when (p.arch) {
            Arch.ARM_NATIVE -> "kvm"
            Arch.X86_EMU    -> "tcg"
        }

        val result = mutableListOf(
            exe,
            "-m",     "${p.ramMb}",
            "-smp",   "${p.cpuCores}",
            "-cpu",   cpuModel,
            "-accel", accel,
            "-drive", "file=${p.vmDir}/disk.vmdk,format=vmdk,if=virtio",
            "-nic",   "user",
            "-serial", "null",
            "-vga",   "virtio",
            "-display", "none",
        )
        // BIOS 固件路径
        val fwPath = if (p.bios == BiosType.UEFI) "ovmf_aarch64.bin" else "bios-256k.bin"
        if (p.arch == Arch.ARM_NATIVE) {
            val resolved = FirmwareResolver.resolve(this, fwPath)
            if (resolved.isNotEmpty()) {
                result.add("-bios")
                result.add(resolved)
            }
        } else {
            result.add("-L")
            result.add(filesDir.absolutePath + "/firmware")
        }
        // TPM 2.0
        if (p.tpmEnabled) {
            result.addAll(listOf(
                "-chardev", "socket,id=tpm0,path=/tmp/tpm-${p.id}.sock",
                "-tpmdev",  "emulator,id=tpm0,chardev=tpm0",
                "-device",  "tpm-tis,tpm=tpm0"
            ))
        }
        // ISO 光盘
        p.disks.filter { it.isIso }.forEach { iso ->
            result.addAll(listOf("-cdrom", iso.path))
        }
        return result
    }

    /** 检查可执行文件是否在 PATH 中 */
    private fun isQemuAvailable(name: String): Boolean {
        return try {
            Runtime.getRuntime().exec(arrayOf("which", name)).inputStream.bufferedReader().readText().trim().isNotEmpty()
        } catch (_: Exception) { false }
    }

    /** ── 状态更新（真实写回 Repository）───────────────── */
    private suspend fun notifyRunning(p: VMProfile) {
        val updated = p.copy(status = VMStatus.RUNNING, lastStartedAt = System.currentTimeMillis())
        withContext(Dispatchers.IO) { repo.save(updated) }
    }
    private suspend fun notifyStopped(p: VMProfile) {
        isRunning = false
        val updated = p.copy(status = VMStatus.STOPPED)
        withContext(Dispatchers.IO) { repo.save(updated) }
        try { stopForeground(STOP_FOREGROUND_REMOVE); stopSelf() } catch (_: Exception) {}
    }
    private suspend fun notifyError(p: VMProfile, msg: String) {
        isRunning = false
        qemuProcess?.destroy()
        qemuProcess = null
        val updated = p.copy(status = VMStatus.ERROR)
        withContext(Dispatchers.IO) { repo.save(updated) }
    }

    /** 显示错误通知 */
    private fun showErrorNotification(profile: VMProfile, msg: String) {
        val pi = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val notif = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("运行失败 — ${profile.name}")
            .setContentText(msg.take(80))
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setColor(getColor(R.color.md_theme_error))
            .setContentIntent(pi)
            .setAutoCancel(true)
            .build()
        getSystemService(NotificationManager::class.java).notify(ERROR_NOTIF_ID, notif)
    }

    /** 运行时更新通知文本 */
    private fun showRunningNotification(profile: VMProfile) {
        val notif = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("南阳虚拟Win — ${profile.name}")
            .setContentText("运行中…")
            .setSmallIcon(android.R.drawable.ic_menu_compass)
            .setOngoing(true)
            .build()
        startForeground(NOTIF_ID, notif)
    }

    override fun onDestroy() {
        (scope.coroutineContext[Job])?.cancel()
        notifier.destroy()
        qemuProcess?.destroy()
        isRunning = false
        super.onDestroy()
    }
}

/** 芯片品牌识别 */
private object DeviceUtilsCompat {
    fun getChipBrand(): String {
        val hw = Build.HARDWARE.lowercase()
        return when {
            "qcom" in hw || "apq" in hw           -> "qcom"
            "mtk" in hw || "mediatek" in hw       -> "mtk"
            "hisilicon" in hw                     -> "hisilicon"
            else                                  -> "other"
        }
    }
}

/** 从 filesDir/firmware 或 assets 加载 ROM */
private object FirmwareResolver {
    fun resolve(ctx: Context, name: String): String {
        val src = ctx.filesDir.resolve("firmware").resolve(name)
        if (src.exists()) return src.absolutePath
        try {
            ctx.assets.open("firmware/$name").use { it.copyTo(src.outputStream()) }
            return src.absolutePath
        } catch (_: Exception) {}
        return ""
    }
}
