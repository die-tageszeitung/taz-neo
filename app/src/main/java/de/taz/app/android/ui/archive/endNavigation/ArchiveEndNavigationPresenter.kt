package de.taz.app.android.ui.archive.endNavigation

import de.taz.app.android.api.models.Feed
import de.taz.app.android.base.BasePresenter
import de.taz.app.android.util.PreferencesHelper

class ArchiveEndNavigationPresenter :
    BasePresenter<ArchiveEndNavigationContract.View, ArchiveEndNavigationDataController>(
        ArchiveEndNavigationDataController::class.java
    ), ArchiveEndNavigationContract.Presenter {

    private val preferencesHelper = PreferencesHelper.getInstance()

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
        val inactiveFeedNames = viewModel?.getInactiveFeedNames() ?: emptySet()
        if (feed.name !in inactiveFeedNames) {
            preferencesHelper.deactivateFeed(feed)
        } else {
            preferencesHelper.activateFeed(feed)
        }
    }

}