package de.taz.app.android.ui.webview.pager

import androidx.lifecycle.LiveData
import de.taz.app.android.api.models.Article
import de.taz.app.android.api.models.Section
import de.taz.app.android.base.BaseContract

interface ArticlePagerContract: BaseContract {
    interface View: BaseContract.View {
        fun setArticles(articles: List<Article>, currentPosition: Int)
    }

    interface Presenter: BaseContract.Presenter {
        fun setInitialArticle(article: Article)
        fun setCurrrentPosition(position: Int)
        fun onBackPressed()
    }

    interface DataController: BaseContract.DataController {
        var currentPosition: Int
        fun setInitialArticle(article: Article)
        fun getSection(): Section?
        fun getArticleList(): LiveData<List<Article>>
    }
}