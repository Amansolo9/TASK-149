package com.fieldtripops.ui.itinerary

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import com.fieldtripops.domain.repository.InventoryRepository
import com.fieldtripops.domain.usecase.SaveItineraryDraftUseCase
import com.fieldtripops.domain.usecase.SubmitBookingUseCase
import com.google.common.truth.Truth.assertThat
import io.mockk.mockk
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/**
 * Tests the wizard step validation. Covers:
 *  - Step 1 rejects blank initials / out-of-range party size
 *  - Step 2 rejects invalid dates and out-of-range spans
 *  - Valid step advances to the next step
 */
class ItineraryWizardViewModelTest {

    @get:Rule val rule = InstantTaskExecutorRule()
    private lateinit var saveUseCase: SaveItineraryDraftUseCase
    private lateinit var submitUseCase: SubmitBookingUseCase
    private lateinit var inventoryRepo: InventoryRepository
    private lateinit var vm: ItineraryWizardViewModel

    @Before fun setup() {
        saveUseCase = mockk(relaxed = true)
        submitUseCase = mockk(relaxed = true)
        inventoryRepo = mockk(relaxed = true)
        vm = ItineraryWizardViewModel(saveUseCase, submitUseCase, inventoryRepo)
    }

    @Test
    fun `step 1 blocks advance when initials blank`() {
        vm.updateForm { copy(initials = "", partySize = "2", itineraryType = "standard") }
        val advanced = vm.goToNext()
        assertThat(advanced).isFalse()
        val state = vm.state.value as ItineraryWizardState.ValidationError
        assertThat(state.errors.any { it.contains("initials", ignoreCase = true) }).isTrue()
    }

    @Test
    fun `step 1 blocks advance when party size out of range`() {
        vm.updateForm { copy(initials = "AB", partySize = "20", itineraryType = "standard") }
        val advanced = vm.goToNext()
        assertThat(advanced).isFalse()
        val state = vm.state.value as ItineraryWizardState.ValidationError
        assertThat(state.errors.any { it.contains("Party size", ignoreCase = true) }).isTrue()
    }

    @Test
    fun `valid step 1 input advances to step 2`() {
        vm.updateForm { copy(initials = "AB", partySize = "2", itineraryType = "standard") }
        assertThat(vm.goToNext()).isTrue()
        assertThat(vm.form.value?.step).isEqualTo(2)
    }

    @Test
    fun `step 2 rejects malformed date`() {
        vm.updateForm { copy(initials = "AB", partySize = "2", itineraryType = "standard") }
        vm.goToNext()
        vm.updateForm { copy(startDateInput = "not-a-date", endDateInput = "also-not-a-date") }
        val advanced = vm.goToNext()
        assertThat(advanced).isFalse()
        val state = vm.state.value as ItineraryWizardState.ValidationError
        assertThat(state.errors).isNotEmpty()
    }

    @Test
    fun `step 2 rejects end before start`() {
        vm.updateForm { copy(initials = "AB", partySize = "2", itineraryType = "standard") }
        vm.goToNext()
        vm.updateForm {
            copy(
                startDateInput = "05/01/2026",
                endDateInput = "04/01/2026"
            )
        }
        val advanced = vm.goToNext()
        assertThat(advanced).isFalse()
        val state = vm.state.value as ItineraryWizardState.ValidationError
        assertThat(state.errors.any { it.contains("End date") }).isTrue()
    }

    @Test
    fun `step 2 rejects span over 365 days`() {
        vm.updateForm { copy(initials = "AB", partySize = "2", itineraryType = "standard") }
        vm.goToNext()
        vm.updateForm {
            copy(
                startDateInput = "01/01/2026",
                endDateInput = "12/31/2027"
            )
        }
        val advanced = vm.goToNext()
        assertThat(advanced).isFalse()
        val state = vm.state.value as ItineraryWizardState.ValidationError
        assertThat(state.errors.any { it.contains("365") }).isTrue()
    }

    @Test
    fun `goBack returns to previous step`() {
        vm.updateForm { copy(initials = "AB", partySize = "2", itineraryType = "standard") }
        vm.goToNext()
        assertThat(vm.form.value?.step).isEqualTo(2)
        assertThat(vm.goBack()).isTrue()
        assertThat(vm.form.value?.step).isEqualTo(1)
    }

    @Test
    fun `goBack at step 1 returns false`() {
        assertThat(vm.goBack()).isFalse()
    }
}
