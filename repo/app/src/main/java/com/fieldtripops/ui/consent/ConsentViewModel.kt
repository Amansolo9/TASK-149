package com.fieldtripops.ui.consent

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fieldtripops.domain.model.ConsentRecord
import com.fieldtripops.domain.usecase.RecordConsentUseCase
import kotlinx.coroutines.launch

class ConsentViewModel(
    private val recordConsentUseCase: RecordConsentUseCase
) : ViewModel() {

    companion object {
        const val TYPE_ANALYTICS = "analytics"
        const val TYPE_CONTACT_NOTES = "contact_notes"
        const val TYPE_MARKETING = "marketing_recommendations"
        const val POLICY_VERSION = "1.0.0"
    }

    private val _state = MutableLiveData<Map<String, Boolean>>(emptyMap())
    val state: LiveData<Map<String, Boolean>> = _state

    private val _message = MutableLiveData<String?>(null)
    val message: LiveData<String?> = _message

    /** Map: consentType -> current consentId (for revoke). */
    private val activeIds = mutableMapOf<String, String>()

    fun load() {
        viewModelScope.launch {
            try {
                val active = recordConsentUseCase.listActive()
                activeIds.clear()
                active.forEach { activeIds[it.consentType] = it.id }
                _state.value = active.associate { it.consentType to true }
            } catch (e: SecurityException) {
                _message.value = "Please sign in"
            }
        }
    }

    fun toggle(consentType: String, granted: Boolean) {
        viewModelScope.launch {
            try {
                if (granted) {
                    val c = recordConsentUseCase.grant(consentType, POLICY_VERSION)
                    activeIds[consentType] = c.id
                    _state.value = (_state.value ?: emptyMap()) + (consentType to true)
                    _message.value = "Granted: $consentType"
                } else {
                    val id = activeIds[consentType]
                    if (id != null) {
                        recordConsentUseCase.revoke(id)
                        activeIds.remove(consentType)
                        _state.value = (_state.value ?: emptyMap()) - consentType
                        _message.value = "Revoked: $consentType"
                    }
                }
            } catch (e: SecurityException) {
                _message.value = "Not authorized: ${e.message}"
            }
        }
    }

    fun clearMessage() { _message.value = null }
}
