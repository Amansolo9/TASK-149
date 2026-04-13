package com.fieldtripops.ui.claims

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.fieldtripops.databinding.ItemClaimRowBinding
import com.fieldtripops.domain.model.ClaimTicket

class ClaimListAdapter(
    private val onClick: (ClaimTicket) -> Unit
) : ListAdapter<ClaimTicket, ClaimListAdapter.VH>(Diff) {

    inner class VH(val binding: ItemClaimRowBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val b = ItemClaimRowBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return VH(b)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val t = getItem(position)
        with(holder.binding) {
            titleText.text = "Ticket ${t.id.take(8)}…"
            subtitleText.text = "${t.state.name} · ${t.classification.name} · ${t.responsibility.name}"
            metaText.text = "Booking ${t.bookingOrderId.take(8)}… · created ${t.createdAt}"
            root.setOnClickListener { onClick(t) }
        }
    }

    object Diff : DiffUtil.ItemCallback<ClaimTicket>() {
        override fun areItemsTheSame(o: ClaimTicket, n: ClaimTicket) = o.id == n.id
        override fun areContentsTheSame(o: ClaimTicket, n: ClaimTicket) = o == n
    }
}
