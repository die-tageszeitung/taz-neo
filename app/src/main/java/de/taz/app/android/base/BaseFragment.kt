package de.taz.app.android.base

import androidx.fragment.app.Fragment
import androidx.lifecycle.LifecycleOwner

abstract class BaseFragment<out PRESENTER: BaseContract.Presenter>: Fragment(), BaseContract.View {

    abstract val presenter: PRESENTER

    override fun getLifecycleOwner(): LifecycleOwner {
        return this
    }

}