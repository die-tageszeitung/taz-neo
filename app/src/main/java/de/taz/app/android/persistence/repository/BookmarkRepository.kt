package de.taz.app.android.persistence.repository

import android.content.Context
import androidx.room.withTransaction
import de.taz.app.android.api.models.Article
import de.taz.app.android.api.models.ArticleStub
import de.taz.app.android.api.models.ArticleStubBookmarkTime
import de.taz.app.android.util.SingletonHolder
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.util.*

class BookmarkRepository
private constructor(applicationContext: Context) : RepositoryBase(applicationContext) {

    companion object : SingletonHolder<BookmarkRepository, Context>(::BookmarkRepository)

    private suspend fun bookmarkArticle(articleFileName: String) {
        val currentDate = Date()
        appDatabase.articleDao()
            .updateBookmarkedTime(ArticleStubBookmarkTime(articleFileName, currentDate))
    }

    private suspend fun debookmarkArticle(articleFileName: String) {
        appDatabase.articleDao()
            .updateBookmarkedTime(ArticleStubBookmarkTime(articleFileName, null))
    }

    suspend fun bookmarkArticle(article: Article) {
        bookmarkArticle(article.key)
    }

    suspend fun debookmarkArticle(article: Article) {
        debookmarkArticle(article.key)
    }

    suspend fun toggleBookmark(article: Article) {
        if (article.bookmarked) {
            debookmarkArticle(article.key)
        } else {
            bookmarkArticle(article.key)
        }
    }

    suspend fun toggleBookmark(articleStub: ArticleStub) {
        if (articleStub.bookmarkedTime != null) {
            debookmarkArticle(articleStub.articleFileName)
        } else {
            bookmarkArticle(articleStub.articleFileName)
        }
    }

    suspend fun getBookmarkedArticleFileNamesForSelection(selectedArticleFileNames: List<String>): List<String> {
        return appDatabase.articleDao()
            .getBookmarkedArticleFileNamesForSelection(selectedArticleFileNames)
    }

    fun getBookmarkStateFlow(articleStub: ArticleStub): Flow<Boolean> {
        return appDatabase.articleDao()
            .getArticleBookmarkTimeFlow(articleStub.articleFileName)
            .map { it.bookmarkedTime != null }
    }

    fun getBookmarkedArticleFileNamesFlow(issueKey: AbstractIssueKey): Flow<List<String>> {
        return appDatabase.articleDao()
            .getBookmarkedArticleFileNamesForIssue(issueKey.feedName, issueKey.date, issueKey.status)
    }

    suspend fun migratePublicBookmarks() {
        appDatabase.withTransaction {
            val bookmarkedArticleStubs = appDatabase.articleDao()
                .getBookmarkedArticlesFlow()
                .first()

            for (articleStub in bookmarkedArticleStubs) {
                val publicArticleFileName = articleStub.articleFileName
                val regularArticleFileName = publicArticleFileName.replace("public.", "")
                if (regularArticleFileName != publicArticleFileName) {
                    appDatabase.articleDao().apply {
                        updateBookmarkedTime(
                            ArticleStubBookmarkTime(
                                regularArticleFileName,
                                articleStub.bookmarkedTime
                            )
                        )
                        updateBookmarkedTime(ArticleStubBookmarkTime(publicArticleFileName, null))
                    }
                }
            }
        }
    }
}