package com.nanyang.virtualwin.service

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import com.nanyang.virtualwin.model.VMStatus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * OTG USB 设备管理器
 * 处理 U盘插入/拔出、权限请求，并将设备信息透传给虚拟机
 */
class USBOtgManager(private val context: Context) {

    private val usbManager = context.getSystemService(Context.USB_SERVICE) as UsbManager
    private val _attachedDevices = MutableStateFlow<List<UsbDevice>>(emptyList())
    val attachedDevices: StateFlow<List<UsbDevice>> = _attachedDevices

    // 广播接收器：监听 USB 插拔
    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(ctx: Context, intent: Intent) {
            when (intent.action) {
                UsbManager.ACTION_USB_DEVICE_ATTACHED -> {
                    val dev = intent.getParcelableExtra<UsbDevice>(UsbManager.EXTRA_DEVICE)
                    if (dev != null) requestPermission(dev)
                }
                UsbManager.ACTION_USB_DEVICE_DETACHED -> refreshDevices()
            }
        }
    }

    init {
        refreshDevices()
        val filter = IntentFilter().apply {
            addAction(UsbManager.ACTION_USB_DEVICE_ATTACHED)
            addAction(UsbManager.ACTION_USB_DEVICE_DETACHED)
        }
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("UnspecifiedRegisterReceiverFlag")
            context.registerReceiver(receiver, filter)
        }
    }

    /** 刷新当前已挂载的 USB 设备列表 */
    fun refreshDevices() {
        val devs = usbManager.deviceList.values.filter { dev ->
            dev != null && usbManager.hasPermission(dev!!)
        }.toList()
        _attachedDevices.value = devs
    }

    /** 请求用户授权访问指定 USB 设备 */
    fun requestPermission(device: UsbDevice) {
        if (usbManager.hasPermission(device)) return
        val flag = PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        val pi = PendingIntent.getBroadcast(
            context, device.deviceId.hashCode(),
            Intent("com.nanyang.virtualwin.USB_PERMISSION"), flag
        )
        usbManager.requestPermission(device, pi)
    }

    /** 获取所有已授权设备路径（使用 deviceName） */
    fun getAuthorizedPaths(): Map<String, String> {
        return usbManager.deviceList.entries.mapNotNull { (name, dev) ->
            if (dev != null && usbManager.hasPermission(dev)) {
                name to dev.deviceName
            } else null
        }.toMap()
    }

    fun destroy() {
        try { context.unregisterReceiver(receiver) } catch (_: Exception) {}
    }
}
