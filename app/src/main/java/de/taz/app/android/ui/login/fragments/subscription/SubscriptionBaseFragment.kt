package de.taz.app.android.ui.login.fragments.subscription

import androidx.annotation.LayoutRes
import de.taz.app.android.ui.login.fragments.LoginBaseFragment
import java.lang.RuntimeException

abstract class SubscriptionBaseFragment(@LayoutRes layout: Int): LoginBaseFragment(layout) {
    abstract fun done(): Boolean

    abstract fun next()

    protected fun ifDoneNext()  {
        if(done()) next()
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