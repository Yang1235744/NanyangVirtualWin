package com.nanyang.virtualwin.util

import android.content.Context
import android.os.BatteryManager
import android.os.Build
import android.os.PowerManager
import androidx.core.content.getSystemService

/**
 * 系统信息工具类
 * 识别 SoC 芯片品牌，用于性能优化策略选择
 */
object DeviceUtils {

    /** 返回芯片厂商标识：qcom / mtK / hisilicon / other */
    fun getChipBrand(): String {
        val hw = Build.HARDWARE.lowercase()
        val brand = Build.BRAND.lowercase()
        return when {
            "qcom" in hw || " Snapdragon" in hw || "apq" in hw -> "qcom"
            "mtk" in hw || "mediatek" in hw -> "mtk"
            "hisilicon" in hw || "kirin" in hw -> "hisilicon"
            else -> "other"
        }
    }

    /** 获取可用物理核心数 */
    fun availableCores(): Int = Runtime.getRuntime().availableProcessors()

    /** 获取总内存（MB） */
    fun totalMemoryMb(context: Context): Long {
        return try {
            val info = android.app.ActivityManager.MemoryInfo()
            (context.getSystemService(android.content.Context.ACTIVITY_SERVICE) as android.app.ActivityManager).getMemoryInfo(info)
            info.totalMem / 1024 / 1024
        } catch (_: Exception) { 4096L }
    }

    /** 当前电量百分比 */
    fun batteryPercent(context: Context): Int {
        val bm = context.getSystemService<BatteryManager>()!!
        return bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
    }

    /** 是否正在充电 */
    fun isCharging(context: Context): Boolean {
        val bm = context.getSystemService<BatteryManager>()!!
        return bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_STATUS) ==
                BatteryManager.BATTERY_STATUS_CHARGING
    }

    /** 忽略电池优化的权限状态（API 26+） */
    fun canIgnoreBatteryOpt(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return true
        val pm = context.getSystemService<PowerManager>()!!
        return pm.isIgnoringBatteryOptimizations(context.packageName)
    }
}
