package de.taz.app.android.ui.archive.endNavigation

import de.taz.app.android.api.models.Feed
import de.taz.app.android.base.BasePresenter

class ArchiveEndNavigationPresenter: BasePresenter<ArchiveEndNavigationContract.View, ArchiveEndNavigationDataController>(
    ArchiveEndNavigationDataController::class.java
), ArchiveEndNavigationContract.Presenter {

    override fun onViewCreated() {
        getView()?.let { view ->
            view.getLifecycleOwner().let {
                viewModel?.apply {
                    observeFeeds(it) { feeds ->
                        view.setFeeds(feeds ?: emptyList())
                    }
                    observeInactiveFeedNames(it) { inactiveFeedNames ->
                        view.setInactiveFeedNames(inactiveFeedNames)
                    }
                }
            }
        }
    }

    override fun onFeedClicked(feed: Feed) {
        // TODO
    }

}