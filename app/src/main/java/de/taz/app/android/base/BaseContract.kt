package de.taz.app.android.base

import androidx.lifecycle.LifecycleOwner

interface BaseContract {
    interface View {
        fun getLifecycleOwner(): LifecycleOwner
    }

    interface Presenter {
        fun onViewCreated()
    }

}