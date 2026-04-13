package com.fieldtripops.ui.reports

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.fieldtripops.databinding.FragmentReportsBinding
import com.fieldtripops.domain.model.Role
import com.fieldtripops.ui.util.AuthorizedFragment
import org.koin.androidx.viewmodel.ext.android.viewModel

class ReportsFragment : AuthorizedFragment() {

    override val requiredRoles: Set<Role> = setOf(Role.Reviewer, Role.Administrator)

    private var _binding: FragmentReportsBinding? = null
    private val binding get() = _binding!!
    private val viewModel: ReportsViewModel by viewModel()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentReportsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        if (!enforceAuthorization(view)) return

        binding.btnBookings.setOnClickListener {
            viewModel.runReport(viewModel.reportRequestForLast90Days(ReportsViewModel.ReportKind.Bookings))
        }
        binding.btnClaims.setOnClickListener {
            viewModel.runReport(viewModel.reportRequestForLast90Days(ReportsViewModel.ReportKind.Claims))
        }
        binding.btnRefunds.setOnClickListener {
            viewModel.runReport(viewModel.reportRequestForLast90Days(ReportsViewModel.ReportKind.Refunds))
        }
        binding.btnGovernance.setOnClickListener {
            viewModel.runReport(viewModel.reportRequestForLast90Days(ReportsViewModel.ReportKind.Governance))
        }
        binding.btnRetention.setOnClickListener {
            viewModel.runReport(viewModel.reportRequestForLast90Days(ReportsViewModel.ReportKind.Retention))
        }
        binding.btnExportSelf.setOnClickListener { viewModel.exportSelf() }

        viewModel.output.observe(viewLifecycleOwner) { binding.resultText.text = it }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
