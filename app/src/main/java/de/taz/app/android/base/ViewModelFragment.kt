package de.taz.app.android.base

import androidx.annotation.LayoutRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import java.lang.reflect.ParameterizedType

abstract class ViewModelFragment<VIEW_MODEL : ViewModel>(
    @LayoutRes layoutResourceId: Int
) : BaseMainFragment(layoutResourceId) {

    val viewModel: VIEW_MODEL by lazy {
        ViewModelProvider(this).get(getViewModelClassByReflection())
    }

    private fun getViewModelClassByReflection(): Class<VIEW_MODEL> {
        var superClass = javaClass.genericSuperclass
        while((superClass as? ParameterizedType) == null) {
            superClass = (superClass as Class<*>).genericSuperclass
        }
        return superClass.actualTypeArguments[0] as Class<VIEW_MODEL>
    }
}

