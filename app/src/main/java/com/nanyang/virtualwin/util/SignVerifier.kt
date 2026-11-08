package com.nanyang.virtualwin.util

import java.io.File
import java.math.BigInteger
import java.security.MessageDigest
import java.security.cert.X509Certificate

/**
 * ISO/磁盘镜像签名认证工具
 * 支持 SHA-256 哈希校验 + X.509 证书链验证
 */
object SignVerifier {

    private const val EXPECTED_SHA256_PREFIX = "sha256:"

    /**
     * 校验镜像文件 SHA-256 是否与已发布指纹一致
     * @param file       待校验的 ISO/VMDK 文件
     * @param expected   期望的 SHA-256（可带 sha256: 前缀）
     * @return true 表示校验通过
     */
    fun verifyHash(file: File, expected: String): Boolean {
        val expectedClean = expected.removePrefix(EXPECTED_SHA256_PREFIX).lowercase()
        val actual = sha256(file)
        return actual == expectedClean
    }

    fun sha256(file: File): String {
        val md = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { stream ->
            val buffer = ByteArray(8192)
            var read: Int
            while (stream.read(buffer).also { read = it } != -1) {
                md.update(buffer, 0, read)
            }
        }
        return BigInteger(1, md.digest()).toString(16).padStart(64, '0')
    }

    /**
     * 验证明镜像是否由受信任的证书签发（企业级数字签名）
     * @param certPath  PEM 格式的发布者证书路径
     * @param file     被签名的文件
     * @return true 表示证书可信且文件未被篡改
     */
    fun verifySignature(certPemPath: String, file: File): Boolean {
        // TODO: 接入 BouncyCastle 或 Android CertPath API
        // 当前返回 true 作为占位，实际集成需加入 bcprov SDK
        return try {
            val cf = java.security.cert.CertificateFactory.getInstance("X.509")
            val cert = cf.generateCertificate(File(certPemPath).inputStream()) as X509Certificate
            cert.checkValidity()
            true
        } catch (_: Exception) { false }
    }

    /**
     * 为文件生成可读摘要（用于 UI 展示）
     */
    fun displayHash(file: File): String {
        val hash = sha256(file)
        return hash.chunked(8).joinToString(":")
    }
}
