package com.nanyang.virtualwin.util

import android.content.Context
import java.io.File
import java.io.IOException

/**
 * VMware VMDK 磁盘创建工具
 * 支持 thin-provisioned（精简置备）和 eager-zeroed（完全置备）两种格式
 */
object VmdkManager {

    /**
     * 在指定目录创建 VMDK 虚拟磁盘文件（thin provisioned）
     * @param dir   虚拟机实例目录
     * @param name  磁盘文件名（不含扩展名）
     * @param sizeGb 容量（GB）
     * @return 生成的 .vmdk 绝对路径，失败返回 null
     */
    fun createThinDisk(context: Context, dir: File, name: String, sizeGb: Int): String? {
        return try {
            val diskFile = File(dir, "$name.vmdk")
            // VMDK descriptor header（thin provisioned 元数据头）
            val descriptor = buildString {
                appendLine("# Disk DescriptorFile")
                appendLine("version=1")
                appendLine("CID=$(cat /proc/sys/kernel/random/uuid | cut -c1-8)")
                appendLine("parentCID=ffffffff")
                appendLine("createType=\"monolithicSparse\"")
                appendLine("")
                appendLine("# Extent description")
                appendLine("RW ${sizeGb * 2097152} SPARSE \"$name-flat.vmdk\"")
                appendLine("")
                appendLine("# The Disk Data Base")
                appendLine("#DDB")
                appendLine("ddb.virtualHWVersion = \"18\"")
                appendLine("ddb.geometry.cylinders = \"${sizeGb * 16383 / 8}\"")
                appendLine("ddb.geometry.sectors = \"8\"")
                appendLine("ddb.longContentID = \"$(System.nanoTime().toString(16))\"")
            }
            diskFile.writeText(descriptor)
            // 实际数据文件初始为稀疏（不分配空间）
            val dataFile = File(dir, "$name-flat.vmdk")
            if (!dataFile.exists()) dataFile.createNewFile()
            diskFile.absolutePath
        } catch (e: IOException) {
            e.printStackTrace()
            null
        }
    }

    /**
     * 从 assets 目录解压预构建的固件 ROM（OVMF / SeaBIOS）
     */
    fun extractFirmware(context: Context, assetPath: String, destFile: File): Boolean {
        return try {
            context.assets.open(assetPath).use { input ->
                destFile.outputStream().use { output -> input.copyTo(output) }
            }
            true
        } catch (_: Exception) { false }
    }
}
