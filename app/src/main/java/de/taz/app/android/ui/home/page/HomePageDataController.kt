package de.taz.app.android.ui.home.page

import androidx.lifecycle.*
import de.taz.app.android.api.models.AuthStatus
import de.taz.app.android.api.models.Feed
import de.taz.app.android.api.models.IssueStub
import de.taz.app.android.base.BaseDataController
import de.taz.app.android.persistence.repository.FeedRepository
import de.taz.app.android.persistence.repository.IssueRepository
import de.taz.app.android.singletons.AuthHelper
import de.taz.app.android.singletons.PREFERENCES_FEEDS_INACTIVE
import de.taz.app.android.singletons.FeedHelper
import de.taz.app.android.util.SharedPreferenceStringSetLiveData
import de.taz.app.android.monkey.observeDistinct

open class HomePageDataController : BaseDataController(), HomePageContract.DataController {

    /**
     * issues to be shown
     */
    private val issueLiveData: LiveData<List<IssueStub>> =
        IssueRepository.getInstance().getAllStubsLiveData()

    override fun getIssueStubs(): List<IssueStub>? {
        return issueLiveData.value
    }

    override fun observeIssueStubs(
        lifeCycleOwner: LifecycleOwner,
        observer: Observer<List<IssueStub>?>
    ) {
        issueLiveData.observe(lifeCycleOwner, observer)
    }

    /**
     * authentication status
     */
    private val authStatus = AuthHelper.getInstance()

    override fun observeAuthStatus(
        lifeCycleOwner: LifecycleOwner,
        observationCallback: (AuthStatus) -> Unit
    ) {
        authStatus.authStatusLiveData.observeDistinct(lifeCycleOwner, observationCallback)
    }

    /**
     * feeds to be used in filtering and endNavigationView
     */
    private val feedsLiveData: LiveData<List<Feed>> =
        FeedRepository.getInstance().getAllLiveData()

    override fun observeFeeds(
        lifeCycleOwner: LifecycleOwner,
        observationCallback: (List<Feed>) -> Unit
    ) {
        feedsLiveData.observeDistinct(lifeCycleOwner, observationCallback)
    }

    /**
     * Set of [String] corresponding to the deactivated [Feed]'s [Feed.name]
     */
    private val inactiveFeedNameLiveData =
        SharedPreferenceStringSetLiveData(
            FeedHelper.getInstance().feedPreferences, PREFERENCES_FEEDS_INACTIVE, emptySet()
        )

    override fun observeInactiveFeedNames(
        lifeCycleOwner: LifecycleOwner,
        observationCallback: (Set<String>) -> Unit
    ) {
        inactiveFeedNameLiveData.observeDistinct(lifeCycleOwner, observationCallback)
    }

    /**
     *
     */

    private val currentPosition = MutableLiveData<Int?>().apply { postValue(null) }

    override fun setCurrentPosition(position: Int) {
        currentPosition.postValue(position)
    }

    override fun getCurrentPosition(): Int? {
        return currentPosition.value
    }

    override fun observeCurrentPosition(
        lifeCycleOwner: LifecycleOwner,
        observationCallback: (Int?) -> Unit
    ) {
        currentPosition.observeDistinct(lifeCycleOwner, observationCallback)
    }

}