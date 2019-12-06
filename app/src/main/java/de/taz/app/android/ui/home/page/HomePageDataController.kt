package de.taz.app.android.ui.home.page

import androidx.lifecycle.*
import de.taz.app.android.api.models.AuthStatus
import de.taz.app.android.api.models.Feed
import de.taz.app.android.api.models.IssueStub
import de.taz.app.android.base.BaseDataController
import de.taz.app.android.persistence.repository.FeedRepository
import de.taz.app.android.persistence.repository.IssueRepository
import de.taz.app.android.util.AuthHelper
import de.taz.app.android.util.PREFERENCES_FEEDS_INACTIVE
import de.taz.app.android.util.PreferencesHelper
import de.taz.app.android.util.SharedPreferenceStringSetLiveData

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
    private val authStatus = AuthHelper.getInstance().authStatus

    override fun observeAuthStatus(
        lifeCycleOwner: LifecycleOwner,
        observer: Observer<AuthStatus>
    ) {
        authStatus.observe(lifeCycleOwner, observer)
    }

    override fun observeAuthStatus(
        lifeCycleOwner: LifecycleOwner,
        observationCallback: (AuthStatus) -> Unit
    ) {
        authStatus.observe(lifeCycleOwner, Observer(observationCallback))
    }
    /**
     * feeds to be used in filtering and endNavigationView
     */
    private val feedsLiveData: LiveData<List<Feed>> =
        FeedRepository.getInstance().getAllLiveData()

    override fun observeFeeds(
        lifeCycleOwner: LifecycleOwner,
        observationCallback: (List<Feed>?) -> Unit
    ) {
        feedsLiveData.observe(
            lifeCycleOwner,
            Observer(observationCallback)
        )
    }

    /**
     * Set of [String] corresponding to the deactivated [Feed]'s [Feed.name]
     */
    private val inactiveFeedNameLiveData = SharedPreferenceStringSetLiveData(
        PreferencesHelper.getInstance().feedPreferences, PREFERENCES_FEEDS_INACTIVE, emptySet()
    )

    override fun observeInactiveFeedNames(
        lifeCycleOwner: LifecycleOwner,
        observationCallback: (Set<String>) -> Unit
    ) {
        inactiveFeedNameLiveData.observe(
            lifeCycleOwner,
            Observer(observationCallback)
        )
    }

}