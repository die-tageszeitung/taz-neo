package de.taz.app.android.ui.main

import android.content.Context
import androidx.annotation.AnimRes
import androidx.annotation.StringRes
import androidx.fragment.app.Fragment
import androidx.lifecycle.LifecycleOwner
import de.taz.app.android.api.interfaces.IssueOperations
import de.taz.app.android.api.interfaces.WebViewDisplayable
import de.taz.app.android.api.models.IssueStub
import de.taz.app.android.api.models.SectionStub
import de.taz.app.android.base.BaseContract

interface MainContract : BaseContract {
    interface View : BaseContract.View {

        fun closeDrawer()

        fun getApplicationContext(): Context

        fun getMainDataController(): DataController

        fun hideKeyboard()

        fun setDrawerIssue(issueOperations: IssueOperations?)

        fun showDrawerFragment(fragment: Fragment)

        fun showInWebView(webViewDisplayable: WebViewDisplayable, @AnimRes enterAnimation: Int = 0, @AnimRes exitAnimation: Int = 0, bookmarksArticle: Boolean = false)

        fun showHome()

        fun showIssue(issueStub: IssueStub)

        fun showMainFragment(fragment: Fragment, @AnimRes enterAnimation: Int = 0, @AnimRes exitAnimation: Int = 0)

        fun showToast(@StringRes stringId: Int)

        fun showToast(string: String)

        fun lockEndNavigationView()

        fun unlockEndNavigationView()

    }

    interface Presenter : BaseContract.Presenter {
        fun showIssue(issueStub: IssueStub)

        fun setDrawerIssue()
    }

    interface DataController {
        fun getIssueStub(): IssueOperations?

        fun setIssueOperations(issueOperations: IssueOperations?)

        fun observeIssueStub(lifeCycleOwner: LifecycleOwner, observationCallback: (IssueOperations?) -> (Unit))
    }

}