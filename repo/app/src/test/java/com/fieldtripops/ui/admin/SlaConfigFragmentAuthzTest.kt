package com.fieldtripops.ui.admin

import android.view.View
import androidx.fragment.app.testing.launchFragmentInContainer
import androidx.navigation.Navigation
import androidx.navigation.testing.TestNavHostController
import androidx.test.core.app.ApplicationProvider
import com.fieldtripops.R
import com.fieldtripops.domain.model.Role
import com.fieldtripops.domain.repository.SlaConfigRepository
import com.fieldtripops.domain.sla.SlaConfig
import com.fieldtripops.domain.usecase.UpdateSlaConfigUseCase
import com.fieldtripops.security.auth.SessionContext
import com.fieldtripops.security.auth.SessionManager
import com.google.common.truth.Truth.assertThat
import io.mockk.coEvery
import io.mockk.mockk
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
 * Fragment-level authorization tests for SlaConfigFragment. Verifies:
 *  - Administrator sees the save button (authorized path)
 *  - Non-admin roles are blocked by AuthorizedFragment (view hidden)
 *
 * Uses a TestNavHostController so the guard's `popBackStack()` call
 * doesn't crash the test.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = android.app.Application::class)
class SlaConfigFragmentAuthzTest {

    private lateinit var repo: SlaConfigRepository
    private lateinit var update: UpdateSlaConfigUseCase
    private lateinit var sessionManager: SessionManager

    @Before fun setup() {
        repo = mockk(relaxed = true)
        update = mockk(relaxed = true)
        sessionManager = SessionManager()
        coEvery { repo.get() } returns SlaConfig.DEFAULT

        stopKoin()
        startKoin {
            modules(module {
                single { repo }
                single { update }
                single { sessionManager }
                viewModel { SlaConfigViewModel(get(), get(), get()) }
            })
        }
    }

    @After fun teardown() { stopKoin() }

    private fun attachNav(v: View) {
        val nav = TestNavHostController(ApplicationProvider.getApplicationContext())
        nav.setGraph(R.navigation.nav_graph)
        nav.setCurrentDestination(R.id.shellFragment)
        Navigation.setViewNavController(v, nav)
    }

    @Test
    fun `administrator sees save button`() {
        sessionManager.set(SessionContext("admin", "A", setOf(Role.Administrator), "s1"))
        val scenario = launchFragmentInContainer<SlaConfigFragment>(
            themeResId = R.style.Theme_FieldTripOps
        )
        scenario.onFragment { fragment ->
            attachNav(fragment.requireView())
            val save = fragment.view!!.findViewById<View>(R.id.saveButton)
            assertThat(save).isNotNull()
        }
    }

    @Test
    fun `traveler session is denied — root view hidden`() {
        sessionManager.set(SessionContext("u1", "U", setOf(Role.Traveler), "s2"))
        val scenario = launchFragmentInContainer<SlaConfigFragment>(
            themeResId = R.style.Theme_FieldTripOps
        )
        scenario.onFragment { fragment ->
            attachNav(fragment.requireView())
            assertThat(fragment.view!!.visibility).isEqualTo(View.GONE)
        }
    }

    @Test
    fun `reviewer session is denied — root view hidden`() {
        sessionManager.set(SessionContext("rev", "R", setOf(Role.Reviewer), "s3"))
        val scenario = launchFragmentInContainer<SlaConfigFragment>(
            themeResId = R.style.Theme_FieldTripOps
        )
        scenario.onFragment { fragment ->
            attachNav(fragment.requireView())
            assertThat(fragment.view!!.visibility).isEqualTo(View.GONE)
        }
    }

    @Test
    fun `agent session is denied — SLA admin is admin-only`() {
        sessionManager.set(SessionContext("ag", "Ag", setOf(Role.Agent), "s4"))
        val scenario = launchFragmentInContainer<SlaConfigFragment>(
            themeResId = R.style.Theme_FieldTripOps
        )
        scenario.onFragment { fragment ->
            attachNav(fragment.requireView())
            assertThat(fragment.view!!.visibility).isEqualTo(View.GONE)
        }
    }
}
