package de.taz.app.android.ui.archive.endNavigation

import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.Observer
import de.taz.app.android.api.models.Feed
import de.taz.app.android.base.BaseContract

interface ArchiveEndNavigationContract {

    interface View: BaseContract.View {

        fun setFeeds(feeds: List<Feed>)

        fun setInactiveFeedNames(inactiveFeedNames: Set<String>)

    }

    interface DataController: BaseContract.DataController {

        fun observeFeeds(lifeCycleOwner: LifecycleOwner, observer: Observer<List<Feed>?>)

        fun observeFeeds(lifeCycleOwner: LifecycleOwner, observationCallback: (List<Feed>?) -> (Unit))

        fun observeInactiveFeedNames(lifeCycleOwner: LifecycleOwner, observer: Observer<Set<String>>)

        fun observeInactiveFeedNames(lifeCycleOwner: LifecycleOwner, observationCallback: (Set<String>) -> (Unit))

    }

    interface Presenter: BaseContract.Presenter {
        fun onFeedClicked(feed: Feed)
    }

}