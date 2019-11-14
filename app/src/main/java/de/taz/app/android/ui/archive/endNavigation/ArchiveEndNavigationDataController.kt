package de.taz.app.android.ui.archive.endNavigation

import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LiveData
import androidx.lifecycle.Observer
import de.taz.app.android.api.models.Feed
import de.taz.app.android.base.BaseDataController
import de.taz.app.android.persistence.repository.FeedRepository
import de.taz.app.android.util.PREFERENCES_FEEDS_INACTIVE
import de.taz.app.android.util.PreferencesHelper
import de.taz.app.android.util.SharedPreferenceStringSetLiveData

open class ArchiveEndNavigationDataController : BaseDataController(),
    ArchiveEndNavigationContract.DataController {

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

    override fun getInactiveFeedNames(): Set<String> {
        return inactiveFeedNameLiveData.value ?: emptySet()
    }

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