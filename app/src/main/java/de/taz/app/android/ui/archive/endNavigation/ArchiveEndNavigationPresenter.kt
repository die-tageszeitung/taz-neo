package de.taz.app.android.ui.archive.endNavigation

import de.taz.app.android.api.models.Feed
import de.taz.app.android.base.BasePresenter

class ArchiveEndNavigationPresenter: BasePresenter<ArchiveEndNavigationContract.View, ArchiveEndNavigationDataController>(
    ArchiveEndNavigationDataController::class.java
), ArchiveEndNavigationContract.Presenter {

    override fun onViewCreated() {
        getView()?.getLifecycleOwner()?.let {
            viewModel?.observeFeeds(it) { feeds ->
                getView()?.setFeeds(feeds ?: emptyList())
            }
        }
    }

    override fun onFeedClicked(feed: Feed) {
        // TODO
    }

}