package com.fieldtripops.ui.shell

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.fieldtripops.databinding.ItemBookingRowBinding
import com.fieldtripops.domain.model.BookingOrder

class BookingListAdapter(
    private val onClick: (BookingOrder) -> Unit,
    private val onLongClick: (BookingOrder) -> Boolean = { false }
) : ListAdapter<BookingOrder, BookingListAdapter.VH>(Diff) {

    inner class VH(val binding: ItemBookingRowBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val binding = ItemBookingRowBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return VH(binding)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val order = getItem(position)
        with(holder.binding) {
            titleText.text = "Booking ${order.id.take(8)}…"
            subtitleText.text = "State: ${order.state.name} · Party: ${order.partySize}"
            metaText.text = "Created: ${order.createdAt}"
            root.setOnClickListener { onClick(order) }
            root.setOnLongClickListener { onLongClick(order) }
        }
    }

    object Diff : DiffUtil.ItemCallback<BookingOrder>() {
        override fun areItemsTheSame(oldItem: BookingOrder, newItem: BookingOrder): Boolean =
            oldItem.id == newItem.id
        override fun areContentsTheSame(oldItem: BookingOrder, newItem: BookingOrder): Boolean =
            oldItem == newItem
    }
}
