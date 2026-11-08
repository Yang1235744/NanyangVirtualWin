package com.nanyang.virtualwin.ui.activity

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.viewpager2.adapter.FragmentStateAdapter
import com.google.android.material.tabs.TabLayoutMediator
import com.nanyang.virtualwin.R
import com.nanyang.virtualwin.databinding.ActivityNewVmBinding
import com.nanyang.virtualwin.model.Arch
import com.nanyang.virtualwin.model.BiosType
import com.nanyang.virtualwin.model.NetworkMode
import com.nanyang.virtualwin.repository.VMRepository
import com.nanyang.virtualwin.util.DeviceUtils
import com.nanyang.virtualwin.util.SignVerifier
import com.nanyang.virtualwin.util.VmdkManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * 新建虚拟机向导 — 4步表单 + 底部确认按钮
 *
 * 每个 StepFragment 持有自身的 UI 状态，通过 lambda 回调更新父级共享数据。
 */
class NewVMActivity : AppCompatActivity() {

    private lateinit var binding: ActivityNewVmBinding

    // ── 共享表单状态（internal 以便 Fragment 访问）───────────────
    data class FormState(
        val name: String = "Windows",
        val arch: Arch = Arch.ARM_NATIVE,
        val bios: BiosType = BiosType.UEFI,
        val tpmEnabled: Boolean = true,
        val cpuCores: Int = 4,
        val ramMb: Int = 4096,
        val diskGb: Int = 32,
        val isoPath: String = "",
        val networkMode: NetworkMode = NetworkMode.BRIDGE,
        val iconBytes: ByteArray? = null
    )
    internal val formState = MutableStateFlow(FormState())
    private val isLoading  = MutableStateFlow(false)

    // ISO/图标选择器
    internal val isoPicker = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let { pickIso(it) }
    }
    internal val iconPicker = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let { pickIcon(it) }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityNewVmBinding.inflate(layoutInflater)
        setContentView(binding.root)

        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = getString(R.string.new_vm_title)

        setupViewPager()
        bindButtons()
        observeLoading()
    }

    private fun setupViewPager() {
        binding.vpSteps.adapter = object : FragmentStateAdapter(this) {
            override fun getItemCount(): Int = 4
            override fun createFragment(position: Int): Fragment = when (position) {
                0 -> InfoStepFragment.newInstance()
                1 -> ConfigStepFragment.newInstance()
                2 -> DiskStepFragment.newInstance()
                else -> NetworkStepFragment.newInstance()
            }
        }
        TabLayoutMediator(binding.tabSteps, binding.vpSteps) { tab, pos ->
            tab.text = when (pos) {
                0 -> "信息"
                1 -> "配置"
                2 -> "磁盘"
                3 -> "网络"
                else -> ""
            }
        }.attach()
    }

    private fun bindButtons() {
        binding.btnPrev.setOnClickListener {
            if (binding.vpSteps.currentItem > 0) binding.vpSteps.currentItem--
            else finish()
        }
        binding.btnNext.setOnClickListener { onNext() }
    }

    private fun onNext() {
        when (binding.vpSteps.currentItem) {
            0 -> {
                val name = formState.value.name
                if (name.isBlank()) {
                    Toast.makeText(this, "请输入虚拟机名称", Toast.LENGTH_SHORT).show()
                    return
                }
                binding.vpSteps.currentItem = 1
            }
            1 -> binding.vpSteps.currentItem = 2
            2 -> binding.vpSteps.currentItem = 3
            3 -> createVm()
        }
    }

    // ── 创建虚拟机 ──────────────────────────────────────────
    private fun createVm() {
        lifecycleScope.launch {
            isLoading.value = true
            try {
                val repo = VMRepository(this@NewVMActivity)
                val vmDir = File(filesDir, "vms/${formState.value.name}").apply { mkdirs() }
                val diskPath = VmdkManager.createThinDisk(this@NewVMActivity, vmDir, "disk", formState.value.diskGb)!!

                val profile = com.nanyang.virtualwin.model.VMProfile(
                    name          = formState.value.name,
                    iconData      = formState.value.iconBytes?.let { android.util.Base64.encodeToString(it, android.util.Base64.NO_WRAP) },
                    arch          = formState.value.arch,
                    bios          = formState.value.bios,
                    tpmEnabled    = formState.value.tpmEnabled,
                    cpuCores      = formState.value.cpuCores,
                    ramMb         = formState.value.ramMb,
                    networkMode   = formState.value.networkMode,
                    chipOpt       = resolveChipOpt(),
                    disks = buildList {
                        add(com.nanyang.virtualwin.model.DiskEntry(
                            path = diskPath,
                            format = com.nanyang.virtualwin.model.DiskFormat.VMDK,
                            sizeGb = formState.value.diskGb,
                            label = "System disk (${formState.value.diskGb}GB)"
                        ))
                        if (formState.value.isoPath.isNotEmpty()) add(
                            com.nanyang.virtualwin.model.DiskEntry(
                                path = formState.value.isoPath,
                                isIso = true,
                                label = "Boot ISO"
                            )
                        )
                    }
                )
                repo.save(profile)
                Toast.makeText(this@NewVMActivity, R.string.vm_created_success, Toast.LENGTH_SHORT).show()
                finish()
            } catch (e: Exception) {
                Toast.makeText(this@NewVMActivity, "${R.string.vm_creation_failed}: ${e.message}", Toast.LENGTH_LONG).show()
            } finally {
                isLoading.value = false
            }
        }
    }

    private fun pickIso(uri: Uri) {
        lifecycleScope.launch {
            val path = resolveUriToPath(uri) ?: return@launch
            val updated = formState.value.copy(isoPath = path)
            formState.value = updated
            // 验签（仅 .iso 文件）
            if (path.endsWith(".iso", ignoreCase = true)) {
                val verified = SignVerifier.verifyHash(File(path), "")
                Toast.makeText(this@NewVMActivity,
                    if (verified) "ISO 签名验证通过" else "未提供指纹（已加载）",
                    Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun pickIcon(uri: Uri) {
        lifecycleScope.launch {
            val bytes = withContext(Dispatchers.IO) {
                contentResolver.openInputStream(uri)?.readBytes()
            }
            formState.value = formState.value.copy(iconBytes = bytes)
        }
    }

    private suspend fun resolveUriToPath(uri: Uri): String? = withContext(Dispatchers.IO) {
        contentResolver.openInputStream(uri)?.use { stream ->
            val dest = File(cacheDir, "tmp_iso_${System.currentTimeMillis()}.iso")
            dest.outputStream().use { out -> stream.copyTo(out) }
            dest.absolutePath
        }
    }

    private fun resolveChipOpt(): com.nanyang.virtualwin.model.ChipOpt = when (DeviceUtils.getChipBrand()) {
        "qcom"     -> com.nanyang.virtualwin.model.ChipOpt.QCOMM
        "mtk"      -> com.nanyang.virtualwin.model.ChipOpt.MEDIATEK
        "hisilicon"-> com.nanyang.virtualwin.model.ChipOpt.HUAWEI_KIRIN
        else       -> com.nanyang.virtualwin.model.ChipOpt.DEFAULT
    }

    private fun observeLoading() {
        lifecycleScope.launch {
            isLoading.collect { loading ->
                binding.progressBar.visibility = if (loading) View.VISIBLE else View.GONE
                binding.btnNext.isEnabled = !loading
            }
        }
    }

    override fun onSupportNavigateUp(): Boolean { finish(); return true }
}

// ══════════════════════════════════════════════════════════
//  各步骤 Fragment（使用 androidx.fragment.app.Fragment）
// ══════════════════════════════════════════════════════════

abstract class StepFragment(private val layoutRes: Int) : Fragment(layoutRes) {
    protected val act get() = requireActivity() as NewVMActivity
}

class InfoStepFragment : StepFragment(R.layout.fragment_step_info) {
    companion object { fun newInstance() = InfoStepFragment() }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        view.findViewById<TextView>(R.id.tvPickIcon)!!.setOnClickListener {
            act.iconPicker.launch("image/*")
        }
        view.findViewById<EditText>(R.id.etVmName)!!.apply {
            setText(act.formState.value.name)
            setOnTextChangedListener { newText ->
                act.formState.value = act.formState.value.copy(name = newText)
            }
        }
    }
}

class ConfigStepFragment : StepFragment(R.layout.fragment_step_config) {
    companion object { fun newInstance() = ConfigStepFragment() }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val act = act
        val maxCpu = DeviceUtils.availableCores()

        // CPU 架构单选
        view.findViewById<RadioGroup>(R.id.rgArch)?.setOnCheckedChangeListener { _, checkedId ->
            act.formState.value = act.formState.value.copy(
                arch = if (checkedId == R.id.rbArm) Arch.ARM_NATIVE else Arch.X86_EMU
            )
        }
        view.findViewById<RadioButton>(R.id.rbArm)?.isChecked = act.formState.value.arch == Arch.ARM_NATIVE
        view.findViewById<RadioButton>(R.id.rbX86)?.isChecked = act.formState.value.arch == Arch.X86_EMU

        // BIOS 类型单选
        view.findViewById<RadioGroup>(R.id.rgBios)?.setOnCheckedChangeListener { _, checkedId ->
            act.formState.value = act.formState.value.copy(
                bios = if (checkedId == R.id.rbUefi) BiosType.UEFI else BiosType.LEGACY
            )
        }
        view.findViewById<RadioButton>(R.id.rbUefi)?.isChecked = act.formState.value.bios == BiosType.UEFI
        view.findViewById<RadioButton>(R.id.rbLegacy)?.isChecked = act.formState.value.bios == BiosType.LEGACY

        // TPM
        view.findViewById<Switch>(R.id.switchTpm)?.isChecked = act.formState.value.tpmEnabled
        view.findViewById<Switch>(R.id.switchTpm)?.setOnCheckedChangeListener { _, v ->
            act.formState.value = act.formState.value.copy(tpmEnabled = v)
        }

        // CPU 核心数
        view.findViewById<SeekBar>(R.id.seekCpu)?.apply {
            max = maxCpu
            progress = act.formState.value.cpuCores
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(sb: SeekBar?, p: Int, f: Boolean) {
                    act.formState.value = act.formState.value.copy(cpuCores = p)
                    view.findViewById<TextView>(R.id.tvCpuCount)?.text = "$p 核"
                }
                override fun onStartTrackingTouch(sb: SeekBar?) {}
                override fun onStopTrackingTouch(sb: SeekBar?) {}
            })
        }
        view.findViewById<TextView>(R.id.tvCpuCount)?.text = "${act.formState.value.cpuCores} 核"

        // 内存选择
        val ramOptions = listOf(1024, 2048, 4096, 6144, 8192, 12288, 16384)
        view.findViewById<Spinner>(R.id.spinnerRam)?.let { spinner ->
            val labels = ramOptions.map { "${it} MB" }
            spinner.adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, labels)
            val idx = ramOptions.indexOf(act.formState.value.ramMb)
            spinner.setSelection(if (idx >= 0) idx else 2)
            spinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                override fun onItemSelected(p: AdapterView<*>?, v: View?, pos: Int, id: Long) {
                    act.formState.value = act.formState.value.copy(ramMb = ramOptions[pos])
                }
                override fun onNothingSelected(p: AdapterView<*>?) {}
            }
        }
    }
}

class DiskStepFragment : StepFragment(R.layout.fragment_step_disk) {
    companion object { fun newInstance() = DiskStepFragment() }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val act = act
        // 硬盘大小滑块
        view.findViewById<SeekBar>(R.id.seekDisk)?.apply {
            max = 200
            progress = act.formState.value.diskGb
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(sb: SeekBar?, p: Int, f: Boolean) {
                    act.formState.value = act.formState.value.copy(diskGb = p)
                    view.findViewById<TextView>(R.id.tvDiskSize)?.text = "${p} GB"
                }
                override fun onStartTrackingTouch(sb: SeekBar?) {}
                override fun onStopTrackingTouch(sb: SeekBar?) {}
            })
        }
        view.findViewById<TextView>(R.id.tvDiskSize)?.text = "${act.formState.value.diskGb} GB"
        view.findViewById<TextView>(R.id.tvIsoPath)?.text = run {
            val p = act.formState.value.isoPath
            if (p.isEmpty()) "尚未选择 ISO" else p
        }
        view.findViewById<Button>(R.id.btnSelectIso)?.setOnClickListener {
            act.isoPicker.launch("*/*")
        }
    }
}

class NetworkStepFragment : StepFragment(R.layout.fragment_step_network) {
    companion object { fun newInstance() = NetworkStepFragment() }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val act = act
        view.findViewById<RadioGroup>(R.id.rgNetwork)?.setOnCheckedChangeListener { _, checkedId ->
            act.formState.value = act.formState.value.copy(
                networkMode = when (checkedId) {
                    R.id.rbBridge -> NetworkMode.BRIDGE
                    R.id.rbNat    -> NetworkMode.NAT
                    else          -> NetworkMode.NONE
                }
            )
        }
        when (act.formState.value.networkMode) {
            NetworkMode.BRIDGE -> view.findViewById<RadioButton>(R.id.rbBridge)?.isChecked = true
            NetworkMode.NAT    -> view.findViewById<RadioButton>(R.id.rbNat)?.isChecked = true
            NetworkMode.NONE   -> view.findViewById<RadioButton>(R.id.rbNone)?.isChecked = true
        }
        view.findViewById<TextView>(R.id.tvNetworkStatus)?.text =
            "WiFi: ${com.nanyang.virtualwin.util.NetworkUtils.getCurrentSsid(requireContext())}"
    }
}

// ── EditText 扩展：监听文本变化 ───────────────────────────
private fun EditText.setOnTextChangedListener(fn: (String) -> Unit) {
    addTextChangedListener(object : android.text.TextWatcher {
        override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
        override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
            fn(s?.toString() ?: "")
        }
        override fun afterTextChanged(s: android.text.Editable?) {}
    })
}
