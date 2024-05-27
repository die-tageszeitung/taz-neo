package de.taz.app.android.base

import androidx.viewbinding.ViewBinding
import de.taz.app.android.util.hideSoftInputKeyboard

abstract class BaseMainFragment<VIEW_BINDING : ViewBinding> : ViewBindingFragment<VIEW_BINDING>() {

    override fun onDetach() {
        hideSoftInputKeyboard()
        super.onDetach()
    }
}