package de.taz.app.android.base

import androidx.annotation.LayoutRes
import androidx.fragment.app.Fragment
import androidx.lifecycle.LifecycleOwner
import de.taz.app.android.ui.main.MainActivity

abstract class BaseFragment<out PRESENTER: BaseContract.Presenter>(
    @LayoutRes layoutResourceId: Int
): Fragment(layoutResourceId), BaseContract.View {

    abstract val presenter: PRESENTER

    override fun getLifecycleOwner(): LifecycleOwner {
        return this
    }

    override fun getMainView(): MainActivity? {
        return activity as? MainActivity
    }

}