package de.taz.app.android.ui.archive

import androidx.lifecycle.LifecycleOwner
import de.taz.app.android.api.models.IssueStub
import de.taz.app.android.base.BaseContract
import de.taz.app.android.ui.main.MainContract

interface ArchiveContract {

    interface View: BaseContract.View {

        fun getMainView(): MainContract.View?

        fun hideScrollView()

        fun onDataSetChanged(issueStubs: List<IssueStub>)

    }

    interface Presenter: BaseContract.Presenter {
        fun onRefresh()

        fun onScroll()

    }

    interface DataController {
        fun getIssueStubs(): List<IssueStub>?

        fun observeIssues(lifeCycleOwner: LifecycleOwner, observationCallback: (List<IssueStub>?) -> (Unit))
    }

}