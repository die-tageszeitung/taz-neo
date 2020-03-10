package de.taz.app.android.ui.moment

import android.content.Context
import android.graphics.Bitmap
import de.taz.app.android.api.models.Feed
import de.taz.app.android.api.models.IssueStub
import de.taz.app.android.base.BaseContract
import de.taz.app.android.singletons.DateFormat

interface MomentViewContract {

    interface View: BaseContract.View {
        fun displayIssue(momentImageBitmap: Bitmap, date: String?, dateFormat: DateFormat)
        fun clearIssue()
        fun getContext(): Context
        fun setDimension(feed: Feed?)
    }

    interface Presenter: BaseContract.Presenter {
        fun clearIssue()
        suspend fun setIssue(issueStub: IssueStub, feed: Feed?, dateFormat: DateFormat)
    }

    interface DataController

}