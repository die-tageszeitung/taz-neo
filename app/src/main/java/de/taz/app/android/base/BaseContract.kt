package de.taz.app.android.base

import androidx.lifecycle.LifecycleOwner
import de.taz.app.android.ui.main.MainContract

interface BaseContract {

    interface View {
        fun getLifecycleOwner(): LifecycleOwner
        fun getMainView(): MainContract.View?
    }

    interface Presenter {
        fun onViewCreated()
    }

    interface DataController

}