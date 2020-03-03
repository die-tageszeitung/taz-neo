package de.taz.app.android.base

import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.LifecycleOwner
import de.taz.app.android.ui.main.MainContract

abstract class BaseActivity: AppCompatActivity(), BaseContract.View {

    override fun getLifecycleOwner(): LifecycleOwner = this

    override fun getMainView(): MainContract.View? = null

}