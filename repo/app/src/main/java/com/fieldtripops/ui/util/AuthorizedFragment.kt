package com.fieldtripops.ui.util

import android.view.View
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.fieldtripops.domain.model.Role
import com.fieldtripops.security.auth.SessionManager
import org.koin.android.ext.android.inject

/**
 * Route-level authorization base class. Subclasses declare which roles are
 * allowed to open the screen via [requiredRoles]. In `onViewCreated` the
 * fragment checks the session and — if unauthorized — navigates back and
 * shows a toast. This is the UI guard; viewmodels/usecases still re-check
 * (defense in depth).
 */
abstract class AuthorizedFragment : Fragment() {

    protected val sessionManager: SessionManager by inject()

    /** Roles allowed to view the screen. `ANY` = any authenticated user. */
    protected abstract val requiredRoles: Set<Role>

    /**
     * Returns true if the session is authorized. Fragments should guard their
     * `onViewCreated` bodies so data loads only proceed after this passes.
     */
    protected fun isAuthorized(): Boolean {
        val session = sessionManager.current() ?: return false
        if (requiredRoles.isEmpty()) return true
        return requiredRoles.any { session.hasRole(it) }
    }

    /**
     * Call from `onViewCreated` BEFORE any data load. If unauthorized,
     * navigates back and returns false so the caller can short-circuit.
     */
    protected fun enforceAuthorization(rootView: View): Boolean {
        if (isAuthorized()) return true
        rootView.visibility = View.GONE
        Toast.makeText(
            requireContext(),
            "Access denied — $requiredRoles role required.",
            Toast.LENGTH_LONG
        ).show()
        findNavController().popBackStack()
        return false
    }
}
