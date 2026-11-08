package com.nanyang.virtualwin

import android.app.Application
import android.content.Context

/**
 * 应用入口 — 初始化全局组件
 */
class NanyangVirtualWinApp : Application() {

    override fun onCreate() {
        super.onCreate()
        instance = this
        // TODO: 初始化 CrashReporter、远程配置、更新检查等
    }

    companion object {
        var instance: Context? = null
            private set
    }
}
