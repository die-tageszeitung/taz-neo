package de.taz.app.android.ui.archive.endNavigation

import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LiveData
import androidx.lifecycle.Observer
import de.taz.app.android.api.models.Feed
import de.taz.app.android.base.BaseDataController
import de.taz.app.android.persistence.repository.FeedRepository

class ArchiveEndNavigationDataController : BaseDataController(),
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

    override fun observeFeeds(lifeCycleOwner: LifecycleOwner, observer: Observer<List<Feed>?>) {
        feedsLiveData.observe(lifeCycleOwner, observer)
    }

}