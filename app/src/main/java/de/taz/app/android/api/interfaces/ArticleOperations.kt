package de.taz.app.android.api.interfaces

import androidx.lifecycle.LiveData
import de.taz.app.android.api.models.*
import de.taz.app.android.persistence.repository.ArticleRepository
import de.taz.app.android.persistence.repository.IssueRepository
import de.taz.app.android.persistence.repository.SectionRepository
import de.taz.app.android.singletons.FileHelper
import java.io.File

interface ArticleOperations: CacheableDownload, WebViewDisplayable  {

    val articleFileName: String
    val articleType: ArticleType
    override val webViewDisplayableKey
        get() = articleFileName

    fun nextArticleStub(): ArticleStub? {
        return ArticleRepository.getInstance().nextArticleStub(articleFileName)
    }

    fun previousArticleStub(): ArticleStub? {
        return ArticleRepository.getInstance().previousArticleStub(articleFileName)
    }

    fun previousArticle(): Article? {
        return ArticleRepository.getInstance().previousArticle(articleFileName)
    }

    fun nextArticle(): Article? {
        return ArticleRepository.getInstance().nextArticle(articleFileName)
    }

    fun getSectionStub(): SectionStub? {
        return SectionRepository.getInstance().getSectionStubForArticle(articleFileName)
    }

    fun getSection(): Section? {
        return SectionRepository.getInstance().getSectionForArticle(articleFileName)
    }

    fun getIndexInSection(): Int? {
        return ArticleRepository.getInstance().getIndexInSection(articleFileName)
    }

    fun isBookmarkedLiveData(): LiveData<Boolean> {
        return ArticleRepository.getInstance().isBookmarkedLiveData(this.articleFileName)
    }

    override fun getFile(): File? {
        return FileHelper.getInstance().getFile(articleFileName)
    }

    override fun previous(): Article? {
        return previousArticle()
    }

    override fun next(): Article? {
        return nextArticle()
    }


    fun isImprint(): Boolean {
        return articleType == ArticleType.IMPRINT
    }

    fun getIssueStub(): IssueStub? {
        return if (isImprint()) {
            IssueRepository.getInstance().getIssueStubByImprintFileName(articleFileName)
        } else {
            getSectionStub()?.issueStub
        }
    }

    fun getIssue(): Issue? {
        return getIssueStub()?.let { IssueRepository.getInstance().getIssue(it) }
    }

    override fun getIssueOperations() = getIssueStub()
}