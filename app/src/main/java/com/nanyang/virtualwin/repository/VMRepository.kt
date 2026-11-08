package com.nanyang.virtualwin.repository

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.nanyang.virtualwin.model.VMProfile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException

/**
 * VMProfile 本地持久化仓库
 * 以 JSON 文件形式存储于 App 外部缓存目录 ~/Android/data/com.nanyang.virtualwin/files/vms/
 */
class VMRepository(private val context: Context) {

    private val gson = Gson()
    private val dir: File by lazy {
        File(context.externalCacheDir, "vms").apply { mkdirs() }
    }

    suspend fun loadAll(): List<VMProfile> = withContext(Dispatchers.IO) {
        if (!dir.exists()) return@withContext emptyList()
        dir.listFiles()?.filter { it.name.endsWith(".json") }?.mapNotNull { file ->
            try {
                gson.fromJson(file.readText(), VMProfile::class.java)
            } catch (_: IOException) { null }
        } ?: emptyList()
    }

    suspend fun save(profile: VMProfile) = withContext(Dispatchers.IO) {
        val file = File(dir, "${profile.id}.json")
        file.writeText(gson.toJson(profile))
        // 同步更新 vmDir 指向实际虚拟机目录
        profile.vmDir = File(File(context.externalCacheDir, "instances"), profile.id).absolutePath
        File(profile.vmDir).mkdirs()
    }

    suspend fun delete(id: String) = withContext(Dispatchers.IO) {
        val file = File(dir, "$id.json")
        if (file.exists()) file.delete()
        // 同时删除实例数据目录
        File(File(context.externalCacheDir, "instances"), id).let { instDir ->
            if (instDir.exists()) deleteRecursively(instDir)
        }
    }

    suspend fun loadById(id: String): VMProfile? = withContext(Dispatchers.IO) {
        val file = File(dir, "$id.json")
        if (!file.exists()) return@withContext null
        try { gson.fromJson(file.readText(), VMProfile::class.java) }
        catch (_: Exception) { null }
    }

    /** 递归删除目录 */
    private fun deleteRecursively(dir: File): Boolean {
        if (dir.isDirectory) dir.listFiles()?.forEach { deleteRecursively(it) }
        return dir.delete()
    }

    /** 扫描已存在实例目录，用于恢复上次会话 */
    suspend fun discoverExisting(): List<VMProfile> = withContext(Dispatchers.IO) {
        val instancesDir = File(context.externalCacheDir, "instances")
        if (!instancesDir.exists()) return@withContext emptyList()
        instancesDir.listFiles()?.mapNotNull { instDir ->
            val profileFile = File(instDir, "profile.json")
            if (profileFile.exists()) {
                try {
                    val p = gson.fromJson(profileFile.readText(), VMProfile::class.java)
                    p.vmDir = instDir.absolutePath
                    p
                } catch (_: Exception) { null }
            } else null
        } ?: emptyList()
    }
}
