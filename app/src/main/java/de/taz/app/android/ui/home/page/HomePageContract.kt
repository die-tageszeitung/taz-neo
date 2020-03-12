package de.taz.app.android.ui.home.page

import android.content.Context
import androidx.lifecycle.LiveData
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
        val authStatusLiveData: LiveData<AuthStatus>
        val currentPositionLiveData: LiveData<Int?>
        val feedsLiveData: LiveData<List<Feed>>
        val inactiveFeedNameLiveData: LiveData<Set<String>>
        val issueStubsLiveData: LiveData<List<IssueStub>>


        fun getIssueStubs(): List<IssueStub>?

        fun setCurrentPosition(position: Int)
        fun getCurrentPosition(): Int?
    }

}
