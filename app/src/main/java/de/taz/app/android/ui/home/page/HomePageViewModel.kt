package de.taz.app.android.ui.home.page

import android.content.Context
import androidx.lifecycle.*
import de.taz.app.android.api.models.Feed
import de.taz.app.android.api.models.IssueStub
import de.taz.app.android.persistence.repository.FeedRepository
import de.taz.app.android.persistence.repository.IssueRepository
import de.taz.app.android.singletons.AuthHelper
import de.taz.app.android.singletons.PREFERENCES_FEEDS_INACTIVE
import de.taz.app.android.singletons.FeedHelper
import de.taz.app.android.util.SharedPreferenceStringSetLiveData

open class HomePageViewModel(applicationContext: Context) : ViewModel() {

    /**
     * issues to be shown
     */
    val issueStubsLiveData: LiveData<List<IssueStub>> =
        IssueRepository.getInstance().getAllStubsLiveData()

    fun getIssueStubs(): List<IssueStub>? {
        return issueStubsLiveData.value
    }

    /**
     * authentication status
     */
    val authStatusLiveData = AuthHelper.getInstance().authStatusLiveData

    /**
     * feeds to be used in filtering and endNavigationView
     */
    val feedsLiveData: LiveData<List<Feed>> =
        FeedRepository.getInstance().getAllLiveData()

    /**
     * Set of [String] corresponding to the deactivated [Feed]'s [Feed.name]
     */
    val inactiveFeedNameLiveData =
        SharedPreferenceStringSetLiveData(
            FeedHelper.getInstance().feedPreferences, PREFERENCES_FEEDS_INACTIVE, emptySet()
        )

    val currentPositionLiveData = MutableLiveData<Int?>().apply { postValue(null) }

    fun setCurrentPosition(position: Int) {
        currentPositionLiveData.postValue(position)
    }

    fun getCurrentPosition(): Int? {
        return currentPositionLiveData.value
    }

}