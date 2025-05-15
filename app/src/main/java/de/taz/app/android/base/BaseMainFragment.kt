package de.taz.app.android.base

import android.os.Bundle
import android.view.View
import androidx.fragment.app.FragmentManager
import androidx.viewbinding.ViewBinding
import de.taz.app.android.util.hideSoftInputKeyboard

abstract class BaseMainFragment<VIEW_BINDING : ViewBinding> : ViewBindingFragment<VIEW_BINDING>() {

    override fun onDetach() {
        hideSoftInputKeyboard()
        super.onDetach()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        childFragmentManager.setupForAccessibility()
    }
}

/**
 * When we add fragments to the stack (instead of replace), we need to disable the accessibility for
 * those not visible. This function disables all accessibility for all except the last (which is on
 * top).
 * Should be called from BaseFragment and BaseActivity.
 */
fun FragmentManager.setupForAccessibility() {
    addOnBackStackChangedListener {
        val lastFragmentWithView = fragments.last { it.view != null }
        for (fragment in fragments) {
            if (fragment == lastFragmentWithView) {
                fragment.view?.importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_YES
            } else {
                fragment.view?.importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
            }
        }
    }
}