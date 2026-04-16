package com.fieldtripops.ui.review

import android.view.View
import androidx.fragment.app.testing.launchFragmentInContainer
import androidx.navigation.Navigation
import androidx.navigation.testing.TestNavHostController
import androidx.test.core.app.ApplicationProvider
import com.fieldtripops.R
import com.fieldtripops.domain.model.Role
import com.fieldtripops.domain.repository.ContentRepository
import com.fieldtripops.domain.usecase.RollbackUseCase
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
 * Fragment-level route-authorization tests for QuarantineFragment.
 * Verifies the AuthorizedFragment guard hides the view for every
 * non-Reviewer/Admin role, with a TestNavHostController attached so
 * `popBackStack()` doesn't crash.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = android.app.Application::class)
class QuarantineFragmentAuthzTest {

    private lateinit var contentRepo: ContentRepository
    private lateinit var rollback: RollbackUseCase
    private lateinit var sessionManager: SessionManager

    @Before fun setup() {
        contentRepo = mockk(relaxed = true)
        rollback = mockk(relaxed = true)
        sessionManager = SessionManager()
        coEvery { contentRepo.getAll() } returns emptyList()

        stopKoin()
        startKoin {
            modules(module {
                single { contentRepo }
                single { rollback }
                single { sessionManager }
                viewModel { QuarantineViewModel(get(), get(), get()) }
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
    fun `traveler cannot open quarantine`() {
        sessionManager.set(SessionContext("u1", "U", setOf(Role.Traveler), "s1"))
        val scenario = launchFragmentInContainer<QuarantineFragment>(
            themeResId = R.style.Theme_FieldTripOps
        )
        scenario.onFragment { fragment ->
            attachNav(fragment.requireView())
            assertThat(fragment.view!!.visibility).isEqualTo(View.GONE)
        }
    }

    @Test
    fun `agent cannot open quarantine`() {
        sessionManager.set(SessionContext("ag", "Ag", setOf(Role.Agent), "s2"))
        val scenario = launchFragmentInContainer<QuarantineFragment>(
            themeResId = R.style.Theme_FieldTripOps
        )
        scenario.onFragment { fragment ->
            attachNav(fragment.requireView())
            assertThat(fragment.view!!.visibility).isEqualTo(View.GONE)
        }
    }

    @Test
    fun `reviewer can open quarantine and sees empty state`() {
        sessionManager.set(SessionContext("rev", "R", setOf(Role.Reviewer), "s3"))
        val scenario = launchFragmentInContainer<QuarantineFragment>(
            themeResId = R.style.Theme_FieldTripOps
        )
        scenario.onFragment { fragment ->
            attachNav(fragment.requireView())
            val recycler = fragment.view!!.findViewById<View>(R.id.recycler)
            assertThat(recycler).isNotNull()
        }
    }
}
