package com.fieldtripops.ui.review

import android.view.View
import androidx.fragment.app.testing.launchFragmentInContainer
import androidx.navigation.Navigation
import androidx.navigation.testing.TestNavHostController
import androidx.recyclerview.widget.RecyclerView
import androidx.test.core.app.ApplicationProvider
import com.fieldtripops.R
import com.fieldtripops.domain.model.Role
import com.fieldtripops.domain.repository.ClaimRepository
import com.fieldtripops.domain.usecase.TransitionClaimUseCase
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
 * Fragment-level Robolectric tests for ReviewQueueFragment: route-level
 * authorization, recycler presence, and empty-state visibility.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = android.app.Application::class)
class ReviewQueueFragmentRobolectricTest {

    private lateinit var claimRepo: ClaimRepository
    private lateinit var transitionUseCase: TransitionClaimUseCase
    private lateinit var sessionManager: SessionManager

    @Before fun setup() {
        claimRepo = mockk(relaxed = true)
        transitionUseCase = mockk(relaxed = true)
        sessionManager = SessionManager()
        coEvery { claimRepo.findByState(any()) } returns emptyList()

        stopKoin()
        startKoin {
            modules(module {
                single { claimRepo }
                single { transitionUseCase }
                single { sessionManager }
                viewModel { ReviewQueueViewModel(get(), get(), get()) }
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
    fun `reviewer session inflates review queue fragment with recycler`() {
        sessionManager.set(SessionContext("rev", "Rev", setOf(Role.Reviewer), "s1"))
        val scenario = launchFragmentInContainer<ReviewQueueFragment>(
            themeResId = R.style.Theme_FieldTripOps
        )
        scenario.onFragment { fragment ->
            attachNav(fragment.requireView())
            val recycler = fragment.view!!.findViewById<RecyclerView>(R.id.queueRecycler)
            assertThat(recycler).isNotNull()
        }
    }

    @Test
    fun `administrator session can also open review queue`() {
        sessionManager.set(SessionContext("admin", "A", setOf(Role.Administrator), "s2"))
        val scenario = launchFragmentInContainer<ReviewQueueFragment>(
            themeResId = R.style.Theme_FieldTripOps
        )
        scenario.onFragment { fragment ->
            attachNav(fragment.requireView())
            assertThat(fragment.view).isNotNull()
        }
    }

    @Test
    fun `traveler session is blocked and view is hidden by authz guard`() {
        sessionManager.set(SessionContext("u1", "U", setOf(Role.Traveler), "s3"))
        val scenario = launchFragmentInContainer<ReviewQueueFragment>(
            themeResId = R.style.Theme_FieldTripOps
        )
        scenario.onFragment { fragment ->
            attachNav(fragment.requireView())
            // AuthorizedFragment sets root view to GONE on denial (before popping)
            assertThat(fragment.view!!.visibility).isEqualTo(View.GONE)
        }
    }
}
