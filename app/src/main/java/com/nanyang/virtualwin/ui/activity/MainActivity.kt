package com.nanyang.virtualwin.ui.activity

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.nanyang.virtualwin.databinding.ActivityMainBinding
import com.nanyang.virtualwin.model.VMProfile
import com.nanyang.virtualwin.service.VMService
import com.nanyang.virtualwin.ui.adapter.VmListAdapter
import com.nanyang.virtualwin.viewmodel.MainViewModel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val vmAdapter = VmListAdapter(onAction = ::onVmAction)
    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        try {
            binding = ActivityMainBinding.inflate(layoutInflater)
            setContentView(binding.root)

            setSupportActionBar(binding.toolbar)
            binding.rvVms.layoutManager = LinearLayoutManager(this)
            binding.rvVms.adapter = vmAdapter

            binding.fabNew.setOnClickListener {
                startActivity(Intent(this, NewVMActivity::class.java))
            }
            binding.btnSettings.setOnClickListener {
                startActivity(Intent(this, SettingsActivity::class.java))
            }

            lifecycleScope.launch {
                viewModel.vms.collectLatest { list ->
                    binding.tvEmpty.visibility = if (list.isEmpty()) View.VISIBLE else View.GONE
                    binding.progressBar.visibility = View.GONE
                    vmAdapter.submitList(list.toList())
                }
            }
            lifecycleScope.launch {
                viewModel.loading.collectLatest { loading ->
                    binding.progressBar.visibility = if (loading) View.VISIBLE else View.GONE
                }
            }
        } catch (e: Exception) {
            // 防止任何意外崩溃导致白屏
            Toast.makeText(this, "初始化异常：${e.message}", Toast.LENGTH_LONG).show()
            finish()
        }
    }

    override fun onResume() {
        super.onResume()
        // 每次回到前台都刷新列表，并在发现卡死状态时自动修复
        viewModel.loadVms()
    }

    private fun onVmAction(profile: VMProfile, action: VmListAdapter.Action) {
        when (action) {
            VmListAdapter.Action.START -> viewModel.toggleStart(profile)
            VmListAdapter.Action.STOP  -> viewModel.stopVm(profile)
            VmListAdapter.Action.DELETE -> {
                lifecycleScope.launch { viewModel.deleteVm(profile.id) }
                Toast.makeText(this, "已删除「${profile.name}」", Toast.LENGTH_SHORT).show()
            }
            VmListAdapter.Action.EDIT -> {
                // TODO: 打开编辑界面
            }
        }
    }
}
