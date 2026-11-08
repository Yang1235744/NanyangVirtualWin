package com.nanyang.virtualwin.ui.adapter

import android.graphics.BitmapFactory
import android.util.Base64
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.nanyang.virtualwin.R
import com.nanyang.virtualwin.databinding.ItemVmCardBinding
import com.nanyang.virtualwin.model.VMProfile
import com.nanyang.virtualwin.model.VMStatus
import com.nanyang.virtualwin.util.DeviceUtils

class VmListAdapter(
    private val onAction: (VMProfile, Action) -> Unit
) : ListAdapter<VMProfile, VmListAdapter.VmViewHolder>(DiffCallback) {

    enum class Action { START, STOP, DELETE, EDIT }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VmViewHolder {
        val binding = ItemVmCardBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return VmViewHolder(binding)
    }

    override fun onBindViewHolder(holder: VmViewHolder, position: Int) {
        holder.bind(getItem(position), onAction)
    }

    inner class VmViewHolder(private val binding: ItemVmCardBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(profile: VMProfile, callback: (VMProfile, Action) -> Unit) {
            // 名称
            binding.tvName.text = profile.name

            // 图标（优先从 base64，否则用默认 Windows 图标）
            val iconBytes = if (!profile.iconData.isNullOrEmpty()) {
                Base64.decode(profile.iconData, Base64.NO_WRAP)
            } else null
            if (iconBytes != null && iconBytes.isNotEmpty()) {
                binding.ivIcon.setImageBitmap(BitmapFactory.decodeByteArray(iconBytes, 0, iconBytes.size))
            } else {
                binding.ivIcon.setImageResource(R.drawable.ic_windows)
            }

            // 状态指示灯 + 文字（数据类避免嵌套 Pair）
            data class StatusInfo(val dotRes: Int, val label: String, val btnSrc: Int, val tint: Int?)
            val si = when (profile.status) {
                VMStatus.STOPPED   -> StatusInfo(R.drawable.circle_grey, "已停止",  R.drawable.ic_play,   null)
                VMStatus.STARTING  -> StatusInfo(R.drawable.circle_yellow, "启动中…",  R.drawable.ic_stop, null)
                VMStatus.RUNNING   -> StatusInfo(R.drawable.circle_green, "运行中",   R.drawable.ic_stop, null)
                VMStatus.PAUSED    -> StatusInfo(R.drawable.circle_yellow, "已暂停",   R.drawable.ic_play, null)
                VMStatus.STOPPING  -> StatusInfo(R.drawable.circle_yellow, "停止中…",  R.drawable.ic_stop, null)
                VMStatus.ERROR     -> StatusInfo(R.drawable.circle_red,    "错误",     R.drawable.ic_play, null)
            }
            binding.vStatusDot.setBackgroundResource(si.dotRes)
            binding.tvStatus.text = si.label
            binding.btnPlay.setImageResource(si.btnSrc)
            si.tint?.let { binding.btnPlay.setColorFilter(it) }

            // 规格信息
            val chipLabel = when (DeviceUtils.getChipBrand()) {
                "qcom"      -> "骁龙"
                "mtk"       -> "天玑"
                "hisilicon" -> "麒麟"
                else        -> "ARM"
            }
            binding.tvSpecs.text = "${profile.cpuCores}核 · ${profile.ramMb / 1024}GB RAM · ${profile.bios.name}"
            binding.tvChipBrand.text = chipLabel

            // 按钮点击
            binding.btnPlay.setOnClickListener {
                val action = if (profile.status == VMStatus.RUNNING || profile.status == VMStatus.STARTING)
                    Action.STOP else Action.START
                callback(profile, action)
            }
            binding.btnDelete.setOnClickListener { callback(profile, Action.DELETE) }
        }
    }

    object DiffCallback : DiffUtil.ItemCallback<VMProfile>() {
        override fun areItemsTheSame(old: VMProfile, new: VMProfile) = old.id == new.id
        override fun areContentsTheSame(old: VMProfile, new: VMProfile) = old == new
    }
}
