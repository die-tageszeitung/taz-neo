package de.taz.app.android.base

import androidx.lifecycle.SavedStateViewModelFactory
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.viewbinding.ViewBinding
import java.lang.reflect.ParameterizedType

abstract class BaseViewModelFragment<
        VIEW_MODEL : ViewModel,
        VIEW_BINDING: ViewBinding
> : BaseMainFragment<VIEW_BINDING>() {

    open val viewModel: VIEW_MODEL by lazy {
        ViewModelProvider(
            this,
            SavedStateViewModelFactory(
                this.requireActivity().application,
                this
            )
        )[getViewModelClassByReflection()]
    }

    @Suppress("UNCHECKED_CAST")
    private fun getViewModelClassByReflection(): Class<VIEW_MODEL> {
        var superClass = javaClass.genericSuperclass
        while((superClass as? ParameterizedType) == null) {
            superClass = (superClass as Class<*>).genericSuperclass
        }
        return superClass.actualTypeArguments[0] as Class<VIEW_MODEL>
    }
}

