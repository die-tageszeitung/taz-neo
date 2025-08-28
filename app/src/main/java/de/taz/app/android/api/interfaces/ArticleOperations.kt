package de.taz.app.android.api.interfaces

import android.content.Context
import de.taz.app.android.api.models.ArticleType
import de.taz.app.android.api.models.IssueStub
import de.taz.app.android.api.models.SectionStub
import de.taz.app.android.persistence.repository.ArticleRepository
import de.taz.app.android.persistence.repository.IssueRepository
import de.taz.app.android.persistence.repository.SectionRepository
import java.util.Date

interface ArticleOperations: WebViewDisplayable, AudioPlayerPlayable {
    val issueFeedName: String
    val issueDate: String
    override val key: String
    val articleType: ArticleType
    override val dateDownload: Date?
    val mediaSyncId: Int?
    val title: String?
    val teaser: String?
    val readMinutes: Int?
    val onlineLink: String?
    val pageNameList: List<String>
    val bookmarkedTime: Date?
    val position: Int
    val percentage: Int
    val chars: Int?
    val words: Int?

    suspend fun getSectionStub(applicationContext: Context): SectionStub? {
        return SectionRepository.getInstance(applicationContext).getSectionStubForArticle(this.key)
    }

    suspend fun getIndexInSection(applicationContext: Context): Int? {
        return ArticleRepository.getInstance(applicationContext).getIndexInSection(this.key)
    }

    suspend fun hasAudio(applicationContext: Context): Boolean {
        return ArticleRepository.getInstance(applicationContext).get(this.key)?.audio != null
    }

    fun isImprint(): Boolean {
        return articleType == ArticleType.IMPRINT
    }

    override suspend fun getIssueStub(applicationContext: Context): IssueStub? {
        return if (isImprint()) {
            IssueRepository.getInstance(applicationContext).getIssueStubByImprintFileName(this.key)
        } else {
            getSectionStub(applicationContext)?.getIssueStub(applicationContext)
        }
    }

    override val audioPlayerPlayableKey: String
        get() = key

    suspend fun getAuthorNames(applicationContext: Context): String
}