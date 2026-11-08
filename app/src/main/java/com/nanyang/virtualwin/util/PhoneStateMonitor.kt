package com.nanyang.virtualwin.util

import android.content.Context
import android.media.AudioManager
import android.os.BatteryManager

/**
 * 手机硬件状态监控
 * 提供电量、音量等实时数据给 VM 触摸层
 */
object PhoneStateMonitor {

    private var batteryPercent: Int = 100
    private var isCharging: Boolean = false
    private var volumeLevel: Int = 0
    private var volumeMax: Int = 0

    fun refresh(context: Context) {
        val bm = context.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
        batteryPercent = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
        isCharging = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_STATUS) ==
                BatteryManager.BATTERY_STATUS_CHARGING

        val am = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        volumeLevel = am.getStreamVolume(android.media.AudioManager.STREAM_MUSIC)
        volumeMax = am.getStreamMaxVolume(android.media.AudioManager.STREAM_MUSIC)
    }

    fun getBatteryPercent(): Int = batteryPercent
    fun isCharging(): Boolean = isCharging
    fun getVolumePercent(): Float = if (volumeMax > 0) volumeLevel.toFloat() / volumeMax else 0f
}
