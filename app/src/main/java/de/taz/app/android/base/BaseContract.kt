package de.taz.app.android.base

import android.os.Bundle
import androidx.fragment.app.Fragment
import androidx.lifecycle.LifecycleOwner
import de.taz.app.android.ui.main.MainActivity

interface BaseContract {

    interface View {

        fun getLifecycleOwner(): LifecycleOwner

        fun getMainView(): MainActivity?

        fun showBottomSheet(fragment: Fragment) = Unit

    }

    interface Presenter {
        fun onViewCreated(savedInstanceState: Bundle?) = Unit
    }

    interface DataController

}