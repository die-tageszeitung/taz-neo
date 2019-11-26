package de.taz.app.android.ui.coverflow

import androidx.lifecycle.LiveData
import androidx.lifecycle.MediatorLiveData
import de.taz.app.android.api.models.Feed
import de.taz.app.android.api.models.IssueStub
import de.taz.app.android.base.BaseContract

interface CoverflowContract {

    interface View: BaseContract.View {
        fun onDatasetChanged(issues: List<IssueStub>, feed: Feed?)
        fun skipToEnd()
    }

    interface Presenter: BaseContract.Presenter {
        suspend fun onRefresh()
    }

    interface DataController {
        val feeds: LiveData<Map<String, Feed>>
        val visibleIssueStubsLiveData: MediatorLiveData<List<IssueStub>?>
    }

}