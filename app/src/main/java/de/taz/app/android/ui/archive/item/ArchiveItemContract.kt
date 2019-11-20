package de.taz.app.android.ui.archive.item

import android.content.Context
import android.graphics.Bitmap
import androidx.lifecycle.LiveData
import de.taz.app.android.api.models.Feed
import de.taz.app.android.api.models.IssueStub
import de.taz.app.android.base.BaseContract

interface ArchiveItemContract {

    interface View: BaseContract.View {
        fun displayIssue(momentImageBitmap: Bitmap, date: String)
        fun clearIssue()
        fun getContext(): Context
        fun setDimension(dimenstionString: String)
    }

    interface Presenter: BaseContract.Presenter {
        fun clearIssue()
        suspend fun setIssue(issueStub: IssueStub, feed: Feed?)
    }

    interface DataController

}