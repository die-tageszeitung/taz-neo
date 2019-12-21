package de.taz.app.android.ui.webview.pager

import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LiveData
import de.taz.app.android.api.models.Article
import de.taz.app.android.api.models.Section
import de.taz.app.android.base.BaseContract

interface ArticlePagerContract: BaseContract {
    interface View: BaseContract.View {
        fun setArticles(articles: List<Article>, currentPosition: Int)
        fun persistPosition(position: Int)
    }

    interface Presenter: BaseContract.Presenter {
        fun setInitialArticle(article: Article)
        fun getCurrentPosition(): Int?
        fun setCurrentPosition(position: Int)
        fun onBackPressed()
    }

    interface DataController: BaseContract.DataController {
        fun getCurrentPosition(): Int
        fun setCurrentPosition(position: Int)
        fun observeCurrentPosition(viewLifecycleOwner: LifecycleOwner, block: (Int) -> Unit)
        fun setInitialArticle(article: Article)
        suspend fun getCurrentSection(): Section?
        fun getArticleList(): LiveData<List<Article>>
    }
}