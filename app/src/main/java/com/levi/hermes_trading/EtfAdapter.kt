package com.levi.hermes_trading.ui.home

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.levi.hermes_trading.R
import com.levi.hermes_trading.model.EtfEntry



class EtfAdapter(private val entries: List<EtfEntry>) :
    RecyclerView.Adapter<EtfAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val name: TextView = view.findViewById(R.id.etfName)
        val value: TextView = view.findViewById(R.id.etfValue)
        val urgency: TextView = view.findViewById(R.id.etfUrgency)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_etf_entry, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val entry = entries[position]
        holder.name.text = entry.name
        holder.value.text = "Value = ${entry.value}"
        holder.urgency.text = "Urgency = ${entry.urgency}"
        holder.itemView.setBackgroundColor(Color.parseColor(entry.color))
    }

    override fun getItemCount() = entries.size
}
