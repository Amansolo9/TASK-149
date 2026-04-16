package com.fieldtripops.ui.login

import androidx.fragment.app.testing.launchFragmentInContainer
import com.fieldtripops.R
import com.fieldtripops.domain.usecase.LoginUseCase
import com.google.common.truth.Truth.assertThat
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.androidx.viewmodel.dsl.viewModel
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.module
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Smoke tests for LoginFragment: fragment inflates, wires its VM, and
 * responds to Login button clicks. Uses Robolectric for JVM-only execution.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = android.app.Application::class)
class LoginFragmentRobolectricTest {

    private lateinit var useCase: LoginUseCase

    @Before
    fun setup() {
        useCase = mockk()
        stopKoin()
        startKoin {
            modules(module {
                single { useCase }
                viewModel { LoginViewModel(get()) }
            })
        }
    }

    @After
    fun teardown() { stopKoin() }

    @Test
    fun `fragment inflates without crash`() {
        val scenario = launchFragmentInContainer<LoginFragment>(themeResId = R.style.Theme_FieldTripOps)
        scenario.onFragment { fragment ->
            assertThat(fragment.view).isNotNull()
        }
    }

    @Test
    fun `blank submit leaves button enabled`() = runTest {
        val scenario = launchFragmentInContainer<LoginFragment>(themeResId = R.style.Theme_FieldTripOps)
        scenario.onFragment { fragment ->
            val button = fragment.view!!.findViewById<android.widget.Button>(R.id.loginButton)
            button.performClick() // no input; VM rejects blank synchronously
            assertThat(button.isEnabled).isTrue()
        }
    }

    @Test
    fun `username and password inputs are present and editable`() {
        val scenario = launchFragmentInContainer<LoginFragment>(themeResId = R.style.Theme_FieldTripOps)
        scenario.onFragment { fragment ->
            val userInput = fragment.view!!.findViewById<android.widget.EditText>(R.id.usernameInput)
            val passInput = fragment.view!!.findViewById<android.widget.EditText>(R.id.passwordInput)
            userInput.setText("alice")
            passInput.setText("pw")
            assertThat(userInput.text.toString()).isEqualTo("alice")
            assertThat(passInput.text.toString()).isEqualTo("pw")
        }
    }

    @Test
    fun `login button is present and clickable`() {
        val scenario = launchFragmentInContainer<LoginFragment>(themeResId = R.style.Theme_FieldTripOps)
        scenario.onFragment { fragment ->
            val button = fragment.view!!.findViewById<android.widget.Button>(R.id.loginButton)
            assertThat(button).isNotNull()
            assertThat(button.isEnabled).isTrue()
        }
    }
}
