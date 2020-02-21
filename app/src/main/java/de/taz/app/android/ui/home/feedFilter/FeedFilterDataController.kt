package de.taz.app.android.ui.home.feedFilter

import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LiveData
import androidx.lifecycle.Observer
import de.taz.app.android.annotation.Mockable
import de.taz.app.android.api.models.Feed
import de.taz.app.android.base.BaseDataController
import de.taz.app.android.persistence.repository.FeedRepository
import de.taz.app.android.singletons.PREFERENCES_FEEDS_INACTIVE
import de.taz.app.android.singletons.FeedHelper
import de.taz.app.android.util.SharedPreferenceStringSetLiveData
import de.taz.app.android.util.observe

@Mockable
class FeedFilterDataController : BaseDataController(),
    FeedFilterContract.DataController {

    /**
     * feeds to be used in filtering and endNavigationView
     */
    private val feedsLiveData: LiveData<List<Feed>> =
        FeedRepository.getInstance().getAllLiveData()

    override fun observeFeeds(
        lifeCycleOwner: LifecycleOwner,
        observationCallback: (List<Feed>?) -> Unit
    ) {
        observe(feedsLiveData, lifeCycleOwner) { feeds -> observationCallback.invoke(feeds) }
    }

    /**
     * Set of [String] corresponding to the deactivated [Feed]'s [Feed.name]
     */
    private val inactiveFeedNameLiveData =
        SharedPreferenceStringSetLiveData(
            FeedHelper.getInstance().feedPreferences, PREFERENCES_FEEDS_INACTIVE, emptySet()
        )

    override fun getInactiveFeedNames(): Set<String> {
        return inactiveFeedNameLiveData.value ?: emptySet()
    }

    override fun observeInactiveFeedNames(
        lifeCycleOwner: LifecycleOwner,
        observationCallback: (Set<String>) -> Unit
    ) {
        observe(inactiveFeedNameLiveData, lifeCycleOwner) { feeds ->
            observationCallback.invoke(
                feeds
            )
        }
    }

}