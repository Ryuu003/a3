package com.focuslock

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.focuslock.databinding.ItemAppBinding

class AppAdapter(
    private val items: List<AppItem>,
    private val onToggle: (AppItem, Boolean) -> Unit
) : RecyclerView.Adapter<AppAdapter.VH>() {

    private var filter = ""
    private val displayedItems get() = if (filter.isBlank()) items
        else items.filter { it.label.contains(filter, ignoreCase = true) }

    inner class VH(val b: ItemAppBinding) : RecyclerView.ViewHolder(b.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val b = ItemAppBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return VH(b)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val item = displayedItems[position]
        with(holder.b) {
            appIcon.setImageDrawable(item.icon)
            appName.text = item.label
            appPackage.text = item.packageName
            appToggle.isChecked = item.isSelected
            appToggle.setOnCheckedChangeListener(null)
            appToggle.setOnCheckedChangeListener { _, checked ->
                item.isSelected = checked
                onToggle(item, checked)
            }
            root.setOnClickListener {
                appToggle.toggle()
            }
        }
    }

    override fun getItemCount() = displayedItems.size

    fun setFilter(query: String) {
        filter = query
        notifyDataSetChanged()
    }
}
