package de.taz.app.android.api.interfaces

import android.content.Context
import androidx.lifecycle.LiveData
import de.taz.app.android.api.models.*
import de.taz.app.android.persistence.repository.ArticleRepository
import de.taz.app.android.persistence.repository.IssueRepository
import de.taz.app.android.persistence.repository.SectionRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.*

interface ArticleOperations {
    val key: String
    val articleType: ArticleType
    val dateDownload: Date?

    fun getSectionStub(applicationContext: Context?): SectionStub? {
        return SectionRepository.getInstance(applicationContext).getSectionStubForArticle(this.key)
    }

    fun getIndexInSection(): Int? {
        return ArticleRepository.getInstance().getIndexInSection(this.key)
    }

    fun isBookmarkedLiveData(): LiveData<Boolean> {
        return ArticleRepository.getInstance().isBookmarkedLiveData(this.key)
    }


    fun isImprint(): Boolean {
        return articleType == ArticleType.IMPRINT
    }

    fun getIssueStub(): IssueStub? {
        return if (isImprint()) {
            IssueRepository.getInstance().getIssueStubByImprintFileName(this.key)
        } else {
            getSectionStub(null)?.getIssueStub()
        }
    }

    suspend fun getNavButton(applicationContext: Context?): Image? = withContext(Dispatchers.IO) {
        return@withContext this@ArticleOperations.getSectionStub(applicationContext)?.getNavButton()
    }
}