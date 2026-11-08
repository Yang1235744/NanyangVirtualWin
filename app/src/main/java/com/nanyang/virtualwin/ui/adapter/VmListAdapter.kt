package com.nanyang.virtualwin.ui.adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.nanyang.virtualwin.databinding.ItemVmCardBinding
import com.nanyang.virtualwin.model.VMProfile
import com.nanyang.virtualwin.model.VMStatus

class VmListAdapter(private val onAction: (VMProfile, Action) -> Unit) :
    ListAdapter<VMProfile, VmListAdapter.VmViewHolder>(Diff) {

    enum class Action { START, STOP, DELETE, EDIT }

    inner class VmViewHolder(val binding: ItemVmCardBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(profile: VMProfile) {
            binding.tvName.text = profile.name
            binding.tvCpu.text = "${profile.cpuCores}核  ·  ${profile.ramMb}MB"
            binding.tvBios.text = "${profile.displayBios()}${if (profile.tpmEnabled) "  TPM" else ""}"
            binding.tvStatus.text = when (profile.status) {
                VMStatus.RUNNING -> binding.root.context.getString(com.nanyang.virtualwin.R.string.vm_running)
                VMStatus.PAUSED  -> binding.root.context.getString(com.nanyang.virtualwin.R.string.vm_paused)
                else             -> binding.root.context.getString(com.nanyang.virtualwin.R.string.vm_stopped)
            }
            val statusColor = when (profile.status) {
                VMStatus.RUNNING -> com.nanyang.virtualwin.R.color.running_green
                VMStatus.PAUSED  -> com.nanyang.virtualwin.R.color.paused_orange
                else             -> com.nanyang.virtualwin.R.color.stopped_grey
            }
            binding.dotStatus.setCardBackgroundColor(
                binding.root.resources.getColor(statusColor, null)
            )

            binding.btnStart.setOnClickListener { onAction(profile, Action.START) }
            binding.btnDelete.setOnClickListener { onAction(profile, Action.DELETE) }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        VmViewHolder(ItemVmCardBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(holder: VmViewHolder, position: Int) =
        holder.bind(getItem(position))

    companion object Diff : DiffUtil.ItemCallback<VMProfile>() {
        override fun areItemsTheSame(o: VMProfile, n: VMProfile) = o.id == n.id
        override fun areContentsTheSame(o: VMProfile, n: VMProfile) = o == n
    }
}
