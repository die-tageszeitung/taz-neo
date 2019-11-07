package de.taz.app.android.ui.archive

import android.graphics.Bitmap
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.Observer
import de.taz.app.android.api.models.IssueStub
import de.taz.app.android.base.BaseContract
import de.taz.app.android.ui.main.MainContract

interface ArchiveContract {

    interface View: BaseContract.View {

        fun getMainView(): MainContract.View?

        fun hideScrollView()

        fun onDataSetChanged(issueStubs: List<IssueStub>)

        fun addBitmap(tag: String, bitmap: Bitmap)

        fun addBitmaps(map: Map<String, Bitmap>)

        fun showIssueDownloadingProgressbar(issueStub: IssueStub)

        fun hideIssueDownloadingProgressbar(issueStub: IssueStub)

    }

    interface Presenter: BaseContract.Presenter {

        fun getNextIssueMoments(date: String, limit: Int)

        fun onItemSelected(issueStub: IssueStub)

        fun onMomentBitmapCreated(tag: String, bitmap: Bitmap)

        fun onRefresh()
    }

    interface DataController {

        fun getIssueStubs(): List<IssueStub>?

        fun observeIssueStubs(lifeCycleOwner: LifecycleOwner, observer: Observer<List<IssueStub>?>)

        fun observeIssueStubs(lifeCycleOwner: LifecycleOwner, observationCallback: (List<IssueStub>?) -> (Unit))

        fun getMomentBitmapMap(): Map<String, Bitmap>

        fun addBitmap(tag: String, bitmap: Bitmap)

        fun getBitmap(tag: String): Bitmap?
    }

}