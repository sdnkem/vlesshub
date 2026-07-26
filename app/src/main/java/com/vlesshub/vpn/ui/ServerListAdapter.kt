package com.vlesshub.vpn.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.vlesshub.vpn.databinding.ItemServerBinding
import com.vlesshub.vpn.model.ServerProfile

class ServerListAdapter(
    private val onSelect: (ServerProfile) -> Unit,
    private val onDelete: (ServerProfile) -> Unit,
    private val onRename: (ServerProfile) -> Unit
) : RecyclerView.Adapter<ServerListAdapter.ViewHolder>() {

    private var items: List<ServerProfile> = emptyList()
    private var activeId: String? = null

    fun submitList(newItems: List<ServerProfile>, activeProfileId: String?) {
        items = newItems
        activeId = activeProfileId
        notifyDataSetChanged()
    }

    inner class ViewHolder(val binding: ItemServerBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemServerBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val profile = items[position]
        holder.binding.remark.text = profile.remark
        holder.binding.subtitle.text = "${profile.protocol.name} · ${profile.address}:${profile.port}"
        holder.binding.radioActive.isChecked = profile.id == activeId
        holder.binding.root.setOnClickListener { onSelect(profile) }
        holder.binding.btnDelete.setOnClickListener { onDelete(profile) }
        holder.binding.btnEdit.setOnClickListener { onRename(profile) }
    }

    override fun getItemCount() = items.size
}
