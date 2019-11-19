package de.taz.app.android.ui.archive.main

import androidx.lifecycle.*
import de.taz.app.android.api.models.Feed
import de.taz.app.android.api.models.IssueStub
import de.taz.app.android.base.BaseDataController
import de.taz.app.android.persistence.repository.FeedRepository
import de.taz.app.android.persistence.repository.IssueRepository
import de.taz.app.android.util.PREFERENCES_FEEDS_INACTIVE
import de.taz.app.android.util.PreferencesHelper
import de.taz.app.android.util.SharedPreferenceStringSetLiveData

open class ArchiveDataController : BaseDataController(),
    ArchiveContract.DataController {

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
            Observer { feeds -> observationCallback.invoke(feeds) }
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
            Observer { feeds -> observationCallback.invoke(feeds) }
        )
    }

}