package de.taz.app.android.api.interfaces

import android.content.Context
import androidx.lifecycle.LiveData
import de.taz.app.android.api.models.*
import de.taz.app.android.persistence.repository.ArticleRepository
import de.taz.app.android.persistence.repository.IssueRepository
import de.taz.app.android.persistence.repository.SectionRepository
import java.util.*

interface ArticleOperations {
    val key: String
    val articleType: ArticleType
    val dateDownload: Date?

    suspend fun getSectionStub(applicationContext: Context): SectionStub? {
        return SectionRepository.getInstance(applicationContext).getSectionStubForArticle(this.key)
    }

    suspend fun getIndexInSection(applicationContext: Context): Int? {
        return ArticleRepository.getInstance(applicationContext).getIndexInSection(this.key)
    }

    suspend fun isBookmarkedLiveData(applicationContext: Context): LiveData<Boolean> {
        return ArticleRepository.getInstance(applicationContext).isBookmarkedLiveData(this.key)
    }


    fun isImprint(): Boolean {
        return articleType == ArticleType.IMPRINT
    }

    suspend fun getIssueStub(applicationContext: Context): IssueStub? {
        return if (isImprint()) {
            IssueRepository.getInstance(applicationContext).getIssueStubByImprintFileName(this.key)
        } else {
            getSectionStub(applicationContext)?.getIssueStub(applicationContext)
        }
    }
}