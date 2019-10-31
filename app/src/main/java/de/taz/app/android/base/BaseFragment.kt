package de.taz.app.android.base

import androidx.fragment.app.Fragment
import androidx.lifecycle.LifecycleOwner

abstract class BaseFragment: Fragment(), BaseContract.View {

    override fun getLifecycleOwner(): LifecycleOwner {
        return this
    }

}