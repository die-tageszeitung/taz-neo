package de.taz.app.android.base

import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProviders
import java.lang.ref.WeakReference

abstract class BasePresenter<VIEW, VIEW_MODEL : ViewModel>(
    private val viewModelClass: Class<VIEW_MODEL>
) : ViewModel() {

    private var view: WeakReference<VIEW>? = null
    var viewModel: VIEW_MODEL? = null

    open fun attach(view: VIEW) {
        this.view = WeakReference(view)

        getView()?.let { it ->
            if (it is FragmentActivity) {
                viewModel = ViewModelProviders.of(it).get(viewModelClass)
            }
            if (it is Fragment) {
                viewModel = ViewModelProviders.of(it).get(viewModelClass)
            }
        }
    }

    fun getView(): VIEW? = view?.get()

}