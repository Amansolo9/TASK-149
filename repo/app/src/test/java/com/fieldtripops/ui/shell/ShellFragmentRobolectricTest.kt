package com.fieldtripops.ui.shell

import android.view.View
import androidx.fragment.app.testing.launchFragmentInContainer
import androidx.navigation.Navigation
import androidx.navigation.testing.TestNavHostController
import androidx.test.core.app.ApplicationProvider
import com.fieldtripops.R
import com.fieldtripops.domain.model.Role
import com.fieldtripops.domain.model.Session
import com.fieldtripops.domain.model.User
import com.fieldtripops.domain.repository.BookingRepository
import com.fieldtripops.domain.repository.UserRepository
import com.fieldtripops.domain.usecase.LogoutUseCase
import com.fieldtripops.domain.usecase.RequestUserDeletionUseCase
import com.fieldtripops.domain.usecase.ValidateSessionUseCase
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
import java.time.Instant

/**
 * Fragment-level behavior tests for ShellFragment. Verifies role-gated card
 * visibility on the real inflated layout using a `TestNavHostController`
 * so nav calls resolve. No emulator needed.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = android.app.Application::class)
class ShellFragmentRobolectricTest {

    private lateinit var validate: ValidateSessionUseCase
    private lateinit var logout: LogoutUseCase
    private lateinit var userRepo: UserRepository
    private lateinit var bookingRepo: BookingRepository
    private lateinit var requestDel: RequestUserDeletionUseCase
    private lateinit var sessionManager: SessionManager

    private val traveler = User(
        id = "u1", username = "alice", displayName = "Alice",
        roles = listOf(Role.Traveler), isActive = true,
        createdAt = Instant.EPOCH, updatedAt = Instant.EPOCH
    )
    private val admin = User(
        id = "a1", username = "admin", displayName = "Admin A",
        roles = listOf(Role.Administrator), isActive = true,
        createdAt = Instant.EPOCH, updatedAt = Instant.EPOCH
    )
    private val validSession = Session(
        id = "s", userId = "u1", startedAt = Instant.EPOCH,
        lastActiveAt = Instant.EPOCH, endedAt = null, endReason = null
    )

    @Before fun setup() {
        validate = mockk()
        logout = mockk(relaxed = true)
        userRepo = mockk()
        bookingRepo = mockk(relaxed = true)
        requestDel = mockk(relaxed = true)
        sessionManager = SessionManager()
        coEvery { bookingRepo.findByTraveler(any()) } returns emptyList()
        coEvery { bookingRepo.findByState(any()) } returns emptyList()

        stopKoin()
        startKoin {
            modules(module {
                single { validate }
                single { logout }
                single { userRepo }
                single { bookingRepo }
                single { requestDel }
                single { sessionManager }
                viewModel { ShellViewModel(get(), get(), get(), get()) }
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
    fun `traveler sees traveler card and my-bookings recycler`() {
        sessionManager.set(SessionContext("u1", "Alice", setOf(Role.Traveler), "s1"))
        coEvery { validate.execute("u1") } returns ValidateSessionUseCase.Result.Valid(validSession)
        coEvery { userRepo.findById("u1") } returns traveler

        val scenario = launchFragmentInContainer<ShellFragment>(themeResId = R.style.Theme_FieldTripOps)
        scenario.onFragment { fragment ->
            attachNav(fragment.requireView())
            val travelerCard = fragment.view!!.findViewById<View>(R.id.travelerCard)
            val agentCard = fragment.view!!.findViewById<View>(R.id.agentCard)
            val adminCard = fragment.view!!.findViewById<View>(R.id.adminCard)
            assertThat(travelerCard.visibility).isEqualTo(View.VISIBLE)
            assertThat(agentCard.visibility).isEqualTo(View.GONE)
            assertThat(adminCard.visibility).isEqualTo(View.GONE)
        }
    }

    @Test
    fun `administrator sees admin card and reports card`() {
        sessionManager.set(SessionContext("a1", "Admin", setOf(Role.Administrator), "s2"))
        coEvery { validate.execute("a1") } returns ValidateSessionUseCase.Result.Valid(validSession.copy(userId = "a1"))
        coEvery { userRepo.findById("a1") } returns admin

        val scenario = launchFragmentInContainer<ShellFragment>(themeResId = R.style.Theme_FieldTripOps)
        scenario.onFragment { fragment ->
            attachNav(fragment.requireView())
            val adminCard = fragment.view!!.findViewById<View>(R.id.adminCard)
            val reportsCard = fragment.view!!.findViewById<View>(R.id.reportsCard)
            assertThat(adminCard.visibility).isEqualTo(View.VISIBLE)
            assertThat(reportsCard.visibility).isEqualTo(View.VISIBLE)
        }
    }

    @Test
    fun `agent sees agent pending confirmation queue card`() {
        sessionManager.set(SessionContext("ag1", "Agent", setOf(Role.Agent), "s3"))
        val agent = traveler.copy(id = "ag1", roles = listOf(Role.Agent))
        coEvery { validate.execute("ag1") } returns ValidateSessionUseCase.Result.Valid(validSession.copy(userId = "ag1"))
        coEvery { userRepo.findById("ag1") } returns agent

        val scenario = launchFragmentInContainer<ShellFragment>(themeResId = R.style.Theme_FieldTripOps)
        scenario.onFragment { fragment ->
            attachNav(fragment.requireView())
            val agentCard = fragment.view!!.findViewById<View>(R.id.agentCard)
            assertThat(agentCard.visibility).isEqualTo(View.VISIBLE)
        }
    }

}
