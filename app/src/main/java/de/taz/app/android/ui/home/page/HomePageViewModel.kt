package de.taz.app.android.ui.home.page

import android.app.Application
import androidx.lifecycle.*
import de.taz.app.android.api.models.Feed
import de.taz.app.android.api.models.IssueStub
import de.taz.app.android.persistence.repository.FeedRepository
import de.taz.app.android.persistence.repository.IssueRepository
import de.taz.app.android.singletons.AuthHelper
import de.taz.app.android.singletons.PREFERENCES_FEEDS_INACTIVE
import de.taz.app.android.singletons.FeedHelper
import de.taz.app.android.util.SharedPreferenceStringSetLiveData

open class HomePageViewModel(application: Application) : AndroidViewModel(application) {

    /**
     * issues to be shown
     */
    val issueStubsLiveData: LiveData<List<IssueStub>> =
        IssueRepository.getInstance(this.getApplication()).getAllStubsLiveData()

    /**
     * authentication status
     */
    val authStatusLiveData = AuthHelper.getInstance(this.getApplication()).authStatusLiveData

    /**
     * feeds to be used in filtering and endNavigationView
     */
    val feedsLiveData: LiveData<List<Feed>> =
        FeedRepository.getInstance(this.getApplication()).getAllLiveData()

    /**
     * Set of [String] corresponding to the deactivated [Feed]'s [Feed.name]
     */
    val inactiveFeedNameLiveData =
        SharedPreferenceStringSetLiveData(
            FeedHelper.getInstance(this.getApplication()).feedPreferences,
            PREFERENCES_FEEDS_INACTIVE,
            emptySet()
        )

}