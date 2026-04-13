package com.fieldtripops.ui.review

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.fieldtripops.databinding.FragmentQuarantineBinding
import com.fieldtripops.databinding.ItemQuarantineRowBinding
import com.fieldtripops.domain.model.ContentItem
import com.fieldtripops.domain.model.Role
import com.fieldtripops.ui.util.AuthorizedFragment
import com.fieldtripops.ui.util.gone
import com.fieldtripops.ui.util.visible
import org.koin.androidx.viewmodel.ext.android.viewModel

class QuarantineFragment : AuthorizedFragment() {

    override val requiredRoles: Set<Role> = setOf(Role.Reviewer, Role.Administrator)

    private var _binding: FragmentQuarantineBinding? = null
    private val binding get() = _binding!!
    private val viewModel: QuarantineViewModel by viewModel()
    private lateinit var adapter: Adapter

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentQuarantineBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        if (!enforceAuthorization(view)) return

        adapter = Adapter { item -> viewModel.restore(item.id, "Reviewer one-tap restore") }
        binding.recycler.layoutManager = LinearLayoutManager(requireContext())
        binding.recycler.adapter = adapter

        viewModel.items.observe(viewLifecycleOwner) { list ->
            adapter.submitList(list)
            if (list.isEmpty()) binding.emptyText.visible() else binding.emptyText.gone()
        }
        viewModel.message.observe(viewLifecycleOwner) { msg ->
            if (msg != null) {
                Toast.makeText(requireContext(), msg, Toast.LENGTH_SHORT).show()
                viewModel.clearMessage()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        if (isAuthorized()) viewModel.load()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private class Adapter(
        val onRestore: (ContentItem) -> Unit
    ) : ListAdapter<ContentItem, Adapter.VH>(Diff) {
        class VH(val binding: ItemQuarantineRowBinding) : RecyclerView.ViewHolder(binding.root)
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = VH(
            ItemQuarantineRowBinding.inflate(
                LayoutInflater.from(parent.context), parent, false
            )
        )
        override fun onBindViewHolder(holder: VH, position: Int) {
            val item = getItem(position)
            holder.binding.titleText.text = item.title
            holder.binding.subtitleText.text = "State=${item.state.name} · avg=${"%.2f".format(item.averageRating)}"
            holder.binding.restoreButton.setOnClickListener { onRestore(item) }
        }
        object Diff : DiffUtil.ItemCallback<ContentItem>() {
            override fun areItemsTheSame(a: ContentItem, b: ContentItem) = a.id == b.id
            override fun areContentsTheSame(a: ContentItem, b: ContentItem) = a == b
        }
    }
}
