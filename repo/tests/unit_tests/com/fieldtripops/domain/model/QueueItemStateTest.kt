package com.fieldtripops.domain.model

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class QueueItemStateTest {

    @Test
    fun `pending to running allowed`() {
        assertThat(QueueItemState.canTransition(QueueItemState.Pending, QueueItemState.Running)).isTrue()
    }

    @Test
    fun `running to retry-scheduled allowed`() {
        assertThat(QueueItemState.canTransition(QueueItemState.Running, QueueItemState.RetryScheduled)).isTrue()
    }

    @Test
    fun `archived is terminal`() {
        assertThat(QueueItemState.canTransition(QueueItemState.Archived, QueueItemState.Pending)).isFalse()
    }

    @Test
    fun `succeeded only goes to archived`() {
        assertThat(QueueItemState.canTransition(QueueItemState.Succeeded, QueueItemState.Archived)).isTrue()
        assertThat(QueueItemState.canTransition(QueueItemState.Succeeded, QueueItemState.Running)).isFalse()
    }
}
