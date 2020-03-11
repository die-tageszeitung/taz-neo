package de.taz.app.android.ui.bottomSheet.datePicker

import androidx.lifecycle.LifecycleOwner
import de.taz.app.android.api.models.ArticleStub
import de.taz.app.android.base.BaseContract

interface DatePickerContract {
    interface View: BaseContract.View {
    }

    interface Presenter: BaseContract.Presenter {
       // fun openBookmarks()

        //fun toggleBookmark()
    }

    interface DataController: BaseContract.DataController {
        //fun getArticleStub(): ArticleStub?

        //fun setArticleStub(articleFileName: String)

        //fun observeIsBookmarked(lifecycleOwner: LifecycleOwner, block: (Boolean) -> Unit)
    }
}