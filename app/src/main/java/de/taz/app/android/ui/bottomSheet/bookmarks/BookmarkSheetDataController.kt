package de.taz.app.android.ui.bottomSheet.bookmarks

import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.Observer
import de.taz.app.android.api.models.ArticleStub
import de.taz.app.android.base.BaseDataController
import de.taz.app.android.persistence.repository.ArticleRepository

class BookmarkSheetDataController : BaseDataController(), BookmarkSheetContract.DataController {

    private var articleStubLiveData: LiveData<ArticleStub?> = MutableLiveData<ArticleStub?>().apply { postValue(null) }

    override fun setArticleStub(articleFileName: String) {
        articleStubLiveData = ArticleRepository.getInstance().getStubLiveData(articleFileName)
    }

    override fun getArticleStub(): ArticleStub? {
        return articleStubLiveData.value
    }

    override fun observeIsBookmarked(lifecycleOwner: LifecycleOwner, block: (Boolean) -> Unit) {
        articleStubLiveData.observe(lifecycleOwner, Observer { articleStub ->
            block(articleStub?.bookmarked == true)
        })
    }
}
