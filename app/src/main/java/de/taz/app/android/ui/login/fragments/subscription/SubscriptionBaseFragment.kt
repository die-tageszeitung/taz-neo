package de.taz.app.android.ui.login.fragments.subscription

import androidx.viewbinding.ViewBinding
import de.taz.app.android.ui.login.fragments.LoginBaseFragment

abstract class SubscriptionBaseFragment<VIEW_BINDING : ViewBinding> :
    LoginBaseFragment<VIEW_BINDING>() {
    abstract fun done(): Boolean

    abstract fun next()

    protected fun ifDoneNext() {
        if (done()) next()
    }

    override fun onDestroyView() {
        try {
            done()
        } catch (re: RuntimeException) {
            // do not persist data as activity has been killed
        }
        super.onDestroyView()
    }

}