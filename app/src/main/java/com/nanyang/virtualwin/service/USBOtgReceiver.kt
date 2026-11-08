package com.nanyang.virtualwin.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.hardware.usb.UsbManager
import android.util.Log

/**
 * OTG 广播接收器 — 处理系统级 USB 插拔事件
 * 通知 USBOtgManager 刷新设备列表并透传给正在运行的 VM
 */
class USBOtgReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "USBOtgReceiver"
    }

    override fun onReceive(ctx: Context, intent: Intent) {
        val action = intent.action
        if (action == UsbManager.ACTION_USB_DEVICE_ATTACHED) {
            val dev = intent.getParcelableExtra<android.hardware.usb.UsbDevice>(UsbManager.EXTRA_DEVICE)
            Log.i(TAG, "USB attached: ${dev?.deviceName} (${dev?.productName})")
            // TODO: 通过 LocalBroadcastManager / LiveData 通知 VMService
        } else if (action == UsbManager.ACTION_USB_DEVICE_DETACHED) {
            Log.i(TAG, "USB detached")
        }
    }
}
