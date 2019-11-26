package de.taz.app.android.ui.archive.main

import android.view.MenuItem
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.Observer
import de.taz.app.android.api.models.Feed
import de.taz.app.android.api.models.IssueStub
import de.taz.app.android.base.BaseContract

interface ArchiveContract {

    interface View: BaseContract.View {

        fun onDataSetChanged(issueStubs: List<IssueStub>)

        fun showProgressbar(issueStub: IssueStub)

        fun hideProgressbar(issueStub: IssueStub)

        fun setFeeds(feeds: List<Feed>)

        fun setInactiveFeedNames(inactiveFeedNames: Set<String>)

    }

    interface Presenter: BaseContract.Presenter {

        suspend fun getNextIssueMoments(date: String, limit: Int)

        suspend fun onItemSelected(issueStub: IssueStub)

        fun onBottomNavigationItemClicked(menuItem: MenuItem)
    }

    interface DataController {

        fun getIssueStubs(): List<IssueStub>?

        fun observeIssueStubs(lifeCycleOwner: LifecycleOwner, observer: Observer<List<IssueStub>?>)

        fun observeInactiveFeedNames(lifeCycleOwner: LifecycleOwner, observationCallback: (Set<String>) -> (Unit))

        fun observeFeeds(lifeCycleOwner: LifecycleOwner, observationCallback: (List<Feed>?) -> (Unit))

   }

}