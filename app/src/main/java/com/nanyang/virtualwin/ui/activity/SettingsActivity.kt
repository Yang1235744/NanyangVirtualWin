package com.nanyang.virtualwin.ui.activity

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.nanyang.virtualwin.R
import com.nanyang.virtualwin.databinding.ActivitySettingsBinding
import com.nanyang.virtualwin.model.ChipOpt
import com.nanyang.virtualwin.util.DeviceUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class SettingsActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySettingsBinding
    private val prefs by lazy { getPreferences(MODE_PRIVATE) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = getString(R.string.settings_title)

        // ── 芯片品牌信息 ───────────────────────────────
        val chip = when (DeviceUtils.getChipBrand()) {
            "qcom"   -> "高通骁龙 (QCOM)"
            "mtk"    -> "联发科天玑 (MTK)"
            "hisilicon" -> "华为麒麟 (HUAWEI)"
            else     -> "其他芯片"
        }
        binding.tvChipInfo.text = "当前设备：$chip  ·  可用核心：${DeviceUtils.availableCores()} 核"

        // ── ChipOpt RadioGroup ─────────────────────────
        val savedChip = prefs.getString(PREF_CHIP_OPT, "") ?: ""
        val currentChipOpt = if (savedChip.isNotEmpty()) parseChipOpt(savedChip) else autoDetectChipOpt()
        bindChipRadio(currentChipOpt)

        binding.rgChipOpt.setOnCheckedChangeListener { _, checkedId ->
            val opt = when (checkedId) {
                R.id.rbQcom     -> ChipOpt.QCOMM
                R.id.rbMtk      -> ChipOpt.MEDIATEK
                R.id.rbHisilicon-> ChipOpt.HUAWEI_KIRIN
                else            -> ChipOpt.DEFAULT
            }
            prefs.edit().putString(PREF_CHIP_OPT, opt.name).apply()
        }

        // ── 默认 CPU 核心数 ───────────────────────────
        val defaultCpu = prefs.getInt(PREF_DEFAULT_CPU, DeviceUtils.availableCores().coerceAtLeast(2))
        binding.edtDefaultCpu.setText(defaultCpu.toString())
        binding.btnSaveDefault.apply {
            setOnClickListener {
                val v = binding.edtDefaultCpu.text.toString().toIntOrNull()?.coerceIn(1, 16)
                    ?: run { Toast.makeText(this@SettingsActivity, "请输入 1-16 之间的整数", Toast.LENGTH_SHORT).show(); return@setOnClickListener }
                prefs.edit().putInt(PREF_DEFAULT_CPU, v).apply()
                Toast.makeText(this@SettingsActivity, "已保存（${v}核）", Toast.LENGTH_SHORT).show()
            }
        }

        // ── 默认内存 (MB) ─────────────────────────────
        val defaultRam = prefs.getInt(PREF_DEFAULT_RAM, if (DeviceUtils.totalMemoryMb(this) > 6000) 4096 else 2048)
        binding.edtDefaultRam.setText(defaultRam.toString())
        binding.btnSaveRam.apply {
            setOnClickListener {
                val v = binding.edtDefaultRam.text.toString().toIntOrNull()?.coerceIn(512, 32768)
                    ?: run { Toast.makeText(this@SettingsActivity, "请输入 512-32768 之间的整数", Toast.LENGTH_SHORT).show(); return@setOnClickListener }
                prefs.edit().putInt(PREF_DEFAULT_RAM, v).apply()
                Toast.makeText(this@SettingsActivity, "已保存（${v}MB）", Toast.LENGTH_SHORT).show()
            }
        }

        // ── 忽略电池优化 ──────────────────────────────
        binding.switchIgnoreBattery.isChecked = DeviceUtils.canIgnoreBatteryOpt(this)
        binding.switchIgnoreBattery.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked && Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                    data = android.net.Uri.parse("package:$packageName")
                }
                startActivity(intent)
            } else {
                binding.switchIgnoreBattery.isChecked = false
            }
        }

        // ── 镜像签名认证开关 ──────────────────────────
        binding.switchSigVerify.isChecked = prefs.getBoolean(PREF_SIG_VERIFY, true)
        binding.switchSigVerify.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean(PREF_SIG_VERIFY, isChecked).apply()
        }

        binding.btnBack.setOnClickListener { finish() }
    }

    private fun bindChipRadio(selected: ChipOpt) {
        val id = when (selected) {
            ChipOpt.QCOMM      -> R.id.rbQcom
            ChipOpt.MEDIATEK   -> R.id.rbMtk
            ChipOpt.HUAWEI_KIRIN-> R.id.rbHisilicon
            else               -> R.id.rbQcom
        }
        binding.rgChipOpt.check(id)
    }

    private fun autoDetectChipOpt(): ChipOpt = when (DeviceUtils.getChipBrand()) {
        "qcom"     -> ChipOpt.QCOMM
        "mtk"      -> ChipOpt.MEDIATEK
        "hisilicon"-> ChipOpt.HUAWEI_KIRIN
        else       -> ChipOpt.DEFAULT
    }

    private fun parseChipOpt(name: String): ChipOpt = try {
        ChipOpt.valueOf(name)
    } catch (_: IllegalArgumentException) { ChipOpt.DEFAULT }

    companion object {
        const val PREF_CHIP_OPT     = "pref_chip_opt"
        const val PREF_DEFAULT_CPU  = "pref_default_cpu_cores"
        const val PREF_DEFAULT_RAM  = "pref_default_ram_mb"
        const val PREF_SIG_VERIFY   = "pref_sig_verify"
    }

    override fun onSupportNavigateUp(): Boolean { finish(); return true }
}
