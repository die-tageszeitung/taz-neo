package de.taz.app.android.base

import android.os.Bundle
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProviders
import java.lang.ref.WeakReference

abstract class BasePresenter<VIEW : BaseContract.View, VIEW_MODEL : BaseDataController>(
    private val viewModelClass: Class<VIEW_MODEL>
) : ViewModel(), BaseContract.Presenter {

    private var view: WeakReference<VIEW>? = null
    var viewModel: VIEW_MODEL? = null

    open fun attach(view: VIEW) {
        this.view = WeakReference(view)

        viewModel = when (view) {
            is FragmentActivity -> ViewModelProviders.of(view).get(viewModelClass)
            is Fragment -> ViewModelProviders.of(view).get(viewModelClass)
            else -> null
        }
    }

    fun getView(): VIEW? = view?.get()

    abstract override fun onViewCreated(savedInstanceState: Bundle?)
}