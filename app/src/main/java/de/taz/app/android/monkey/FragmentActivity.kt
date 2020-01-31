package de.taz.app.android.monkey

import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProviders
import de.taz.app.android.base.BaseViewModelFactory


inline fun <reified T : ViewModel> FragmentActivity.getViewModel(noinline creator: (() -> T)? = null): T {
    return if (creator == null) {
        ViewModelProviders.of(this).get(T::class.java)
    } else {
        val get = ViewModelProviders.of(this, BaseViewModelFactory(creator)).get(T::class.java)
        get
    }
}