package com.fieldtripops.ui.login

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.fieldtripops.R
import com.fieldtripops.databinding.FragmentLoginBinding
import com.fieldtripops.ui.util.gone
import com.fieldtripops.ui.util.hideKeyboard
import com.fieldtripops.ui.util.textString
import com.fieldtripops.ui.util.visible
import org.koin.androidx.viewmodel.ext.android.viewModel
import java.time.ZoneId
import java.time.format.DateTimeFormatter

class LoginFragment : Fragment() {

    private var _binding: FragmentLoginBinding? = null
    private val binding get() = _binding!!
    private val viewModel: LoginViewModel by viewModel()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentLoginBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.loginButton.setOnClickListener {
            hideKeyboard()
            val username = binding.usernameInput.textString()
            val password = binding.passwordInput.textString()
            viewModel.login(username, password)
        }

        viewModel.state.observe(viewLifecycleOwner) { state ->
            when (state) {
                is LoginState.Idle -> {
                    binding.loadingIndicator.gone()
                    binding.errorText.gone()
                    binding.loginButton.isEnabled = true
                }
                is LoginState.Loading -> {
                    binding.loadingIndicator.visible()
                    binding.errorText.gone()
                    binding.loginButton.isEnabled = false
                }
                is LoginState.Success -> {
                    binding.loadingIndicator.gone()
                    binding.errorText.gone()
                    findNavController().navigate(R.id.action_login_to_shell)
                }
                is LoginState.Error -> {
                    binding.loadingIndicator.gone()
                    binding.errorText.text = state.message
                    binding.errorText.visible()
                    binding.loginButton.isEnabled = true
                }
                is LoginState.LockedOut -> {
                    binding.loadingIndicator.gone()
                    val formatter = DateTimeFormatter.ofPattern("HH:mm:ss")
                        .withZone(ZoneId.systemDefault())
                    val timeStr = formatter.format(state.unlockAt)
                    binding.errorText.text = getString(R.string.login_error_locked, timeStr)
                    binding.errorText.visible()
                    binding.loginButton.isEnabled = true
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
