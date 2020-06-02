package de.taz.app.android.ui.home.feedFilter

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import de.taz.app.android.annotation.Mockable
import de.taz.app.android.api.models.Feed
import de.taz.app.android.persistence.repository.FeedRepository
import de.taz.app.android.singletons.PREFERENCES_FEEDS_INACTIVE
import de.taz.app.android.singletons.FeedHelper
import de.taz.app.android.util.SharedPreferenceStringSetLiveData

@Mockable
class FeedFilterViewModel(application: Application) : AndroidViewModel(application) {

    /**
     * feeds to be used in filtering and endNavigationView
     */
    val feedsLiveData: LiveData<List<Feed>> =
        FeedRepository.getInstance(application).getAllLiveData()

    /**
     * Set of [String] corresponding to the deactivated [Feed]'s [Feed.name]
     */
    val inactiveFeedNameLiveData =
        SharedPreferenceStringSetLiveData(
            FeedHelper.getInstance(application).feedPreferences,
            PREFERENCES_FEEDS_INACTIVE,
            emptySet()
        )

    fun getInactiveFeedNames(): Set<String> {
        return inactiveFeedNameLiveData.value ?: emptySet()
    }
}