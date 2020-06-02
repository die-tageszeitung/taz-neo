package de.taz.app.android.ui.bottomSheet.bookmarks

import android.app.Application
import androidx.lifecycle.*
import de.taz.app.android.api.models.ArticleStub
import de.taz.app.android.persistence.repository.ArticleRepository
import kotlinx.coroutines.Dispatchers

class BookmarkSheetViewModel(application: Application) : AndroidViewModel(application) {

    private val articleRepository: ArticleRepository = ArticleRepository.getInstance(application)
    private val articleFileNameLiveData: MutableLiveData<String?> = MutableLiveData(null)

    var articleFileName
        get() = articleFileNameLiveData.value
        set(value) { articleFileNameLiveData.value = value }

    private val articleLiveData: LiveData<ArticleStub?> = articleFileNameLiveData.switchMap {
        liveData(viewModelScope.coroutineContext + Dispatchers.IO) {
            it?.let {
                emitSource(articleRepository.getStubLiveData(it))
            } ?: emit(null)
        }
    }

    val articleStub: ArticleStub?
        get() = articleLiveData.value

    val isBookmarkedLiveData: LiveData<Boolean> =
        articleLiveData.map { article -> article?.bookmarked ?: false }
}