package com.nanyang.virtualwin.util

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.wifi.WifiManager
import androidx.core.content.getSystemService

/**
 * 网络诊断与分配工具
 * 支持桥接、NAT、无网络三种模式检测
 */
object NetworkUtils {

    /** 获取 WiFi SSID（需要 WiFi 权限） */
    fun getCurrentSsid(context: Context): String {
        val wm = context.getSystemService<WifiManager>() ?: return "Unknown"
        return wm.connectionInfo?.ssid?.removeSurrounding("\"") ?: "Disconnected"
    }

    /** 当前是否有网络连接 */
    fun isConnected(context: Context): Boolean {
        val cm = context.getSystemService<ConnectivityManager>() ?: return false
        val net = cm.activeNetwork ?: return false
        return cm.getNetworkCapabilities(net)
            ?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
    }

    /** 获取本地 IPv4 地址 */
    fun localIp(context: Context): String {
        val wm = context.getSystemService<WifiManager>() ?: return "0.0.0.0"
        return intToIp(wm.dhcpInfo.gateway or 0)
    }

    private fun intToIp(i: Int): String {
        return "%d.%d.%d.%d".format(i and 0xFF, i shr 8 and 0xFF, i shr 16 and 0xFF, i shr 24 and 0xFF)
    }
}

fun Int.toIpString() = "%d.%d.%d.%d".format(this and 0xFF, this shr 8 and 0xFF,
    this shr 16 and 0xFF, this shr 24 and 0xFF)
