package com.fieldtripops.ui.review

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fieldtripops.domain.model.ContentItem
import com.fieldtripops.domain.model.ContentState
import com.fieldtripops.domain.repository.ContentRepository
import com.fieldtripops.domain.usecase.RollbackUseCase
import com.fieldtripops.security.auth.AccessControl
import com.fieldtripops.security.auth.SessionManager
import kotlinx.coroutines.launch
import java.time.Instant

/**
 * One-tap rollback surface for quarantined content. Presents all items
 * currently in Quarantined state; Reviewer/Admin can restore them to their
 * last valid state with a single tap. All restores route through
 * `RollbackUseCase` which finds the most recent valid checkpoint and
 * applies a compensating write to restore entity state, then writes an
 * audit entry. Falls back to no-op with user message if no checkpoint exists.
 */
class QuarantineViewModel(
    private val contentRepository: ContentRepository,
    private val rollbackUseCase: RollbackUseCase,
    private val sessionManager: SessionManager
) : ViewModel() {

    private val _items = MutableLiveData<List<ContentItem>>(emptyList())
    val items: LiveData<List<ContentItem>> = _items

    private val _message = MutableLiveData<String?>(null)
    val message: LiveData<String?> = _message

    fun load() {
        viewModelScope.launch {
            try {
                val session = sessionManager.requireSession()
                AccessControl.requireReviewerOrAdmin(session, "quarantine.read")
                _items.value = contentRepository.getAll()
                    .filter { it.state == ContentState.Quarantined }
            } catch (e: SecurityException) {
                _items.value = emptyList()
                _message.value = "Not authorized to view quarantine"
            }
        }
    }

    fun restore(contentId: String, reason: String) {
        viewModelScope.launch {
            try {
                val session = sessionManager.requireSession()
                AccessControl.requireReviewerOrAdmin(session, "quarantine.rollback")

                val result = rollbackUseCase.execute(
                    entityType = "ContentItem",
                    entityId = contentId,
                    actor = session.userId
                ) { snapshotJson ->
                    // Compensating write: parse the snapshot state and restore
                    // the content item to its pre-quarantine state.
                    val previousState = parseStateFromSnapshot(snapshotJson)
                    contentRepository.updateState(
                        contentId,
                        previousState,
                        Instant.now()
                    )
                }

                when (result) {
                    is RollbackUseCase.Result.RolledBack -> {
                        _message.value = "Rolled back to checkpoint '${result.checkpoint.label}'"
                    }
                    is RollbackUseCase.Result.NoCheckpointFound -> {
                        _message.value = "No rollback checkpoint found for this item"
                    }
                }
                load()
            } catch (e: SecurityException) {
                _message.value = "Not authorized: ${e.message}"
            }
        }
    }

    fun clearMessage() { _message.value = null }

    private fun parseStateFromSnapshot(snapshotJson: String): ContentState {
        // The checkpoint snapshot stores the previous state name.
        // Extract the state field from the JSON snapshot.
        val statePattern = """"state"\s*:\s*"(\w+)"""".toRegex()
        val match = statePattern.find(snapshotJson)
        return if (match != null) {
            try {
                ContentState.valueOf(match.groupValues[1])
            } catch (_: IllegalArgumentException) {
                ContentState.Active
            }
        } else {
            // Default to Active if snapshot format is unrecognized
            ContentState.Active
        }
    }
}
