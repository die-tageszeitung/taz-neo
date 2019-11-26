package de.taz.app.android.ui.main

import android.content.Context
import androidx.annotation.AnimRes
import androidx.annotation.StringRes
import androidx.fragment.app.Fragment
import androidx.lifecycle.LifecycleOwner
import de.taz.app.android.api.interfaces.WebViewDisplayable
import de.taz.app.android.api.models.Issue
import de.taz.app.android.base.BaseContract

interface MainContract : BaseContract {
    interface View : BaseContract.View {

        fun closeDrawer()

        fun getApplicationContext(): Context

        fun getMainDataController(): DataController

        fun showDrawerFragment(fragment: Fragment)

        fun showInWebView(webViewDisplayable: WebViewDisplayable, @AnimRes enterAnimation: Int = 0, @AnimRes exitAnimation: Int = 0)

        fun showFeed()

        fun showMainFragment(fragment: Fragment, @AnimRes enterAnimation: Int = 0, @AnimRes exitAnimation: Int = 0)

        fun showToast(@StringRes stringId: Int)

        fun showToast(string: String)

        fun lockEndNavigationView()

        fun unlockEndNavigationView()

    }

    interface Presenter : BaseContract.Presenter

    interface DataController {
        fun getIssue(): Issue?

        fun setIssue(issue: Issue)

        fun observeIssue(lifeCycleOwner: LifecycleOwner, observationCallback: (Issue?) -> (Unit))

        fun observeIssueIsDownloaded(
            lifeCycleOwner: LifecycleOwner,
            observationCallback: (Boolean) -> (Unit)
        )
    }

}