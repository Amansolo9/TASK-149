package com.fieldtripops.domain.model

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class ContentStateTest {
    @Test
    fun `active to demoted allowed`() {
        assertThat(ContentState.canTransition(ContentState.Active, ContentState.Demoted)).isTrue()
    }

    @Test
    fun `active to excluded not allowed directly`() {
        assertThat(ContentState.canTransition(ContentState.Active, ContentState.Excluded)).isFalse()
    }

    @Test
    fun `demoted to active allowed (reviewer restore)`() {
        assertThat(ContentState.canTransition(ContentState.Demoted, ContentState.Active)).isTrue()
    }

    @Test
    fun `quarantined to excluded allowed`() {
        assertThat(ContentState.canTransition(ContentState.Quarantined, ContentState.Excluded)).isTrue()
    }

    @Test
    fun `excluded can be restored to active`() {
        assertThat(ContentState.canTransition(ContentState.Excluded, ContentState.Active)).isTrue()
    }
}
