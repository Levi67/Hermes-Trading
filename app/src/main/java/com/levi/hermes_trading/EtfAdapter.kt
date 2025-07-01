package com.levi.hermes_trading.ui.home // Or your adapters package

import android.graphics.Color
import android.util.Log
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.levi.hermes_trading.databinding.ItemEtfEntryBinding // Generated from item_etf_entry.xml
import com.levi.hermes_trading.model.EtfEntry

class EtfAdapter : ListAdapter<EtfEntry, EtfAdapter.EtfViewHolder>(EtfDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): EtfViewHolder {
        val binding = ItemEtfEntryBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return EtfViewHolder(binding)
    }

    override fun onBindViewHolder(holder: EtfViewHolder, position: Int) {
        val item = getItem(position)
        holder.bind(item)
    }

    class EtfViewHolder(private val binding: ItemEtfEntryBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(etf: EtfEntry) {
            binding.etfName.text = etf.name
            binding.etfValue.text = "Value: ${etf.value}" // Added "Value: " prefix for clarity
            binding.etfUrgency.text = "Urgency: ${etf.urgency}" // Added "Urgency: " prefix

            try {
                // Set the background color of the card
                binding.etfCard.setCardBackgroundColor(Color.parseColor(etf.color))
            } catch (e: IllegalArgumentException) {
                Log.w("EtfAdapter", "Invalid color string for ${etf.name}: ${etf.color}", e)
                // Optionally set a default color if parsing fails
                binding.etfCard.setCardBackgroundColor(Color.LTGRAY)
            }
        }
    }

    class EtfDiffCallback : DiffUtil.ItemCallback<EtfEntry>() {
        override fun areItemsTheSame(oldItem: EtfEntry, newItem: EtfEntry): Boolean {
            return oldItem.name == newItem.name // Assuming name is unique identifier
        }

        override fun areContentsTheSame(oldItem: EtfEntry, newItem: EtfEntry): Boolean {
            return oldItem == newItem
        }
    }
}