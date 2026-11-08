package com.nanyang.virtualwin.ui.activity

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import com.nanyang.virtualwin.R
import com.nanyang.virtualwin.databinding.ActivitySettingsBinding
import com.nanyang.virtualwin.util.DeviceUtils

class SettingsActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySettingsBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.setTitle(R.string.settings_title)

        // 显示芯片品牌
        val chip = when (DeviceUtils.getChipBrand()) {
            "qcom"   -> "高通骁龙 (QCOM)"
            "mtk"    -> "联发科天玑 (MTK)"
            "hisilicon" -> "华为麒麟 (HUAWEI)"
            else     -> "其他芯片"
        }
        binding.tvChipInfo.text = "当前设备：$chip  |  可用核心：${DeviceUtils.availableCores()} 核"

        // 忽略电池优化开关
        binding.switchIgnoreBattery.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked && Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                    data = android.net.Uri.parse("package:$packageName")
                }
                startActivity(intent)
            }
        }

        binding.btnBack.setOnClickListener { finish() }
    }

    override fun onSupportNavigateUp(): Boolean { finish(); return true }
}
