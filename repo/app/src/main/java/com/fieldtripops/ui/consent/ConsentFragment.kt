package com.fieldtripops.ui.consent

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.fieldtripops.databinding.FragmentConsentBinding
import org.koin.androidx.viewmodel.ext.android.viewModel

class ConsentFragment : Fragment() {

    private var _binding: FragmentConsentBinding? = null
    private val binding get() = _binding!!
    private val viewModel: ConsentViewModel by viewModel()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentConsentBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        bind(binding.analyticsSwitch, ConsentViewModel.TYPE_ANALYTICS)
        bind(binding.contactNotesSwitch, ConsentViewModel.TYPE_CONTACT_NOTES)
        bind(binding.marketingSwitch, ConsentViewModel.TYPE_MARKETING)

        viewModel.state.observe(viewLifecycleOwner) { map ->
            // Set without firing listeners.
            setChecked(binding.analyticsSwitch, map[ConsentViewModel.TYPE_ANALYTICS] == true)
            setChecked(binding.contactNotesSwitch, map[ConsentViewModel.TYPE_CONTACT_NOTES] == true)
            setChecked(binding.marketingSwitch, map[ConsentViewModel.TYPE_MARKETING] == true)
        }
        viewModel.message.observe(viewLifecycleOwner) { msg ->
            if (msg != null) {
                Toast.makeText(requireContext(), msg, Toast.LENGTH_SHORT).show()
                binding.messageText.text = msg
                viewModel.clearMessage()
            }
        }

        viewModel.load()
    }

    private fun bind(sw: com.google.android.material.materialswitch.MaterialSwitch, type: String) {
        sw.setOnCheckedChangeListener { _, isChecked ->
            // Avoid re-entrancy when we programmatically set the value.
            if (sw.tag == "programmatic") return@setOnCheckedChangeListener
            viewModel.toggle(type, isChecked)
        }
    }

    private fun setChecked(sw: com.google.android.material.materialswitch.MaterialSwitch, checked: Boolean) {
        sw.tag = "programmatic"
        sw.isChecked = checked
        sw.tag = null
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
