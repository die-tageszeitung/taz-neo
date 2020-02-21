package de.taz.app.android.ui.home.page

import android.content.Context
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.Observer
import de.taz.app.android.api.models.AuthStatus
import de.taz.app.android.api.models.Feed
import de.taz.app.android.api.models.IssueStub
import de.taz.app.android.base.BaseContract

interface HomePageContract {

    interface View : BaseContract.View {

        fun getContext(): Context?

        fun onDataSetChanged(issueStubs: List<IssueStub>)

        fun setAuthStatus(authStatus: AuthStatus)

        fun setFeeds(feeds: List<Feed>)

        fun setInactiveFeedNames(inactiveFeedNames: Set<String>)

    }

    interface Presenter : BaseContract.Presenter {

        fun getView(): View?

        suspend fun getNextIssueMoments(date: String)

        suspend fun onItemSelected(issueStub: IssueStub)

        fun getCurrentPosition(): Int?

        fun setCurrentPosition(position: Int)

    }

    interface DataController {

        fun getIssueStubs(): List<IssueStub>?

        fun observeAuthStatus(
            lifeCycleOwner: LifecycleOwner,
            observationCallback: (AuthStatus) -> (Unit)
        )

        fun observeIssueStubs(lifeCycleOwner: LifecycleOwner, observer: Observer<List<IssueStub>?>)

        fun observeInactiveFeedNames(
            lifeCycleOwner: LifecycleOwner,
            observationCallback: (Set<String>) -> (Unit)
        )

        fun observeFeeds(
            lifeCycleOwner: LifecycleOwner,
            observationCallback: (List<Feed>) -> (Unit)
        )

        fun setCurrentPosition(position: Int)
        fun getCurrentPosition(): Int?
        fun observeCurrentPosition(
            lifeCycleOwner: LifecycleOwner,
            observationCallback: (Int?) -> Unit
        )
    }

}
