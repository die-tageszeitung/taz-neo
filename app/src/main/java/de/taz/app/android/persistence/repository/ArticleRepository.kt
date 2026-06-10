package de.taz.app.android.persistence.repository

import android.content.Context
import android.database.sqlite.SQLiteConstraintException
import de.taz.app.android.api.models.Article
import de.taz.app.android.api.models.ArticleStub
import de.taz.app.android.api.models.ArticleWithSectionKey
import de.taz.app.android.api.models.FileEntry
import de.taz.app.android.api.models.Image
import de.taz.app.android.api.models.StorageType
import de.taz.app.android.persistence.join.ArticleAuthorImageJoin
import de.taz.app.android.persistence.join.ArticleImageJoin
import de.taz.app.android.sentry.SentryWrapper
import de.taz.app.android.util.SingletonHolder
import java.util.Date


class ArticleRepository private constructor(applicationContext: Context) :
    RepositoryBase(applicationContext) {
    companion object : SingletonHolder<ArticleRepository, Context>(::ArticleRepository)

    private val fileEntryRepository = FileEntryRepository.getInstance(applicationContext)
    private val imageRepository = ImageRepository.getInstance(applicationContext)
    private val audioRepository = AudioRepository.getInstance(applicationContext)

    private suspend fun update(articleStub: ArticleStub) {
        appDatabase.articleDao().insertOrReplace(articleStub)
    }

    /**
     * Save the [Article] to the database and replace any existing [Article] with the same key.
     *
     * This will recursively save all the related models.
     *
     * This method must be called as part of a transaction, for example when saving a [Section].
     * As there are many-to-many relations, replacing an existing [Article] might result in some
     * orphaned children that have to be cleanup up by some scrubber process.
     */
    suspend fun saveInternal(article: Article) {
        var articleToSave = article
        articleToSave = articleToSave.copy(
            articleStub = articleToSave.articleStub.copy(bookmarkedTime = articleToSave.articleStub.bookmarkedTime)
        )

        val articleFileName = articleToSave.articleFileName

        // [ArticleStub.audioFileName] references the [AudioStub.fileName] as a ForeignKey,
        // thus the [AudioStub] must be saved before the [ArticleStub] to fulfill the constraint.
        articleToSave.audio?.let { audio ->
            audioRepository.saveInternal(audio)
        }

        articleToSave.pdf?.let { pdf ->
            fileEntryRepository.save(pdf)
        }

        appDatabase.articleDao().insertOrReplace(articleToSave.articleStub)

        // save html file
        articleToSave.articleHtml?.let { fileEntryRepository.save(it) }

        // save images and relations
        imageRepository.saveInternal(articleToSave.imageList)
        appDatabase.articleImageJoinDao().apply {
            deleteRelationToArticle(articleFileName)
            articleToSave.imageList.forEachIndexed { index, image ->
                insertOrReplace(ArticleImageJoin(articleFileName, image.name, index))
            }
        }
        articleToSave.icon?.let { imageRepository.saveInternal(it) }

        // save authors
        appDatabase.articleAuthorImageJoinDao().apply {
            deleteRelationToArticle(articleFileName)
            articleToSave.authorList.forEachIndexed { index, author ->
                // TODO(peter) Check if it's okay to not have an image of author
                author.imageAuthor?.let { fileEntryRepository.save(it) }
                insertOrReplace(
                    ArticleAuthorImageJoin(
                        articleFileName, author.name, author.imageAuthor?.name, index
                    )
                )
            }
        }
    }

    suspend fun get(articleFileName: String): Article? {
        return appDatabase.articleDao().get(articleFileName)
    }

    suspend fun getByMediaSyncId(articleMediaSyncId: Int): Article? {
        return appDatabase.articleDao().getByMediaSyncId(articleMediaSyncId)
    }

    suspend fun getSectionArticlesByArticleName(articleName: String): List<Article> {
        var articles = appDatabase.articleDao().getSectionArticleListByArticle(articleName)
        // if it is the imprint we want to return a list of it
        if (articles.isEmpty()) {
            articles = get(articleName)?.let {
                listOf(it)
            } ?: emptyList()
        }
        return articles
    }

    suspend fun nextArticleKey(articleName: String): String? {
        return appDatabase.sectionArticleJoinDao().getNextArticleKeyInSection(articleName)
            ?: appDatabase.sectionArticleJoinDao().getNextArticleKeyInNextSection(articleName)
    }


    suspend fun previousArticleKey(articleName: String): String? {
        return appDatabase.sectionArticleJoinDao().getPreviousArticleKeyInSection(articleName)
            ?: appDatabase.sectionArticleJoinDao().getPreviousArticleKeyInPreviousSection(
                articleName
            )
    }

    suspend fun getImagesForArticle(articleFileName: String): List<Image> {
        return appDatabase.articleImageJoinDao().getImagesForArticle(articleFileName)
    }

    suspend fun getIndexInSection(articleName: String): Int? {
        return appDatabase.sectionArticleJoinDao().getIndexOfArticleInSection(articleName)?.plus(1)
    }

    suspend fun deleteArticle(article: Article) {
        appDatabase.articleDao().get(article.key)?.let {
            val articleStub = article.articleStub
            if (it.bookmarkedTime == null) {
                val articleFileName = article.articleFileName

                // Delete all author relations of this Article,
                // but keep the actual FileEntries and rely on the Scrubber to clean them later
                appDatabase.articleAuthorImageJoinDao().deleteRelationToArticle(articleFileName)

                // delete html file
                article.articleHtml?.let { fileEntry -> fileEntryRepository.delete(fileEntry) }

                // delete icon
                article.icon?.let { image ->
                    try {
                        imageRepository.delete(image)
                    } catch (e: SQLiteConstraintException) {
                        // do not delete - still used by section/otherIssue/bookmarked article
                        log.info("Could not delete icon $image as it is still referenced")
                    }
                }

                // Delete related images
                appDatabase.articleImageJoinDao().deleteRelationToArticle(articleFileName)
                article.imageList
                    // Only delete Image Metadata if they are associated with this Articles Issue
                    .filter { image ->
                        image.storageType == StorageType.issue
                    }
                    .forEach { image ->
                        try {
                            imageRepository.delete(image)
                        } catch (e: SQLiteConstraintException) {
                            // do not delete - still used by section/otherIssue/bookmarked article
                            log.info("Could not delete Image ${image.name} as it is still referenced")
                        }
                    }

                try {
                    appDatabase.articleDao().delete(articleStub)
                } catch (e: Exception) {
                    // if an issue has no imprint, it uses an imprint of an older issue. That is why it cannot be deleted here.
                    log.error("Could not delete Article ${articleStub.articleFileName} as it is still referenced")
                    SentryWrapper.captureMessage("Could not delete Article")
                    // TODO need to refactor this
                }

                // After the article has been deleted (and the foreign key reference is removed), we try to delete the audio entry
                article.audio?.let { audio ->
                    audioRepository.tryDelete(audio)
                }
            }
        }
    }

    suspend fun getArticlesForIssue(
        issueKey: IssueKey
    ): List<ArticleWithSectionKey> {
        val articleStubList = appDatabase.articleDao()
            .getArticleStubListForIssue(issueKey.feedName, issueKey.date, issueKey.status)
            .toMutableList()

        // Add imprint to the article list - if it exists
        appDatabase.articleDao().getImprintArticleStubForIssue(issueKey.feedName, issueKey.date)
            ?.let { imprintStub ->
                articleStubList.add(imprintStub)
            }

        val sectionArticleJoinList = appDatabase.sectionArticleJoinDao()
            .getSectionArticleJoinsForIssue(issueKey.feedName, issueKey.date, issueKey.status)
        val articleSectionMap =
            sectionArticleJoinList.associate { it.articleFileName to it.sectionFileName }

        return articleStubList.map {
            val sectionKey = articleSectionMap[it.articleFileName]
            ArticleWithSectionKey(it, sectionKey)
        }
    }

    suspend fun getArticleListForIssue(issueKey: IssueKey): List<Article> {
        val articleList = appDatabase.articleDao()
            .getArticlesWithDetailsForIssue(issueKey.feedName, issueKey.date, issueKey.status)
            .toMutableList()

        // Add imprint to the article list - if it exists
        appDatabase.articleDao().getImprintForIssue(issueKey.feedName, issueKey.date)
            ?.let { imprintWithDetails ->
                articleList.add(imprintWithDetails)
            }

        return articleList
    }

    suspend fun setDownloadDate(
        articleStub: ArticleStub,
        date: Date?
    ) {
        update(articleStub.copy(dateDownload = date))
    }

    suspend fun getDownloadDate(
        articleStub: ArticleStub
    ): Date? {
        return appDatabase.articleDao().getDownloadStatus(articleStub.articleFileName)
    }


    /**
     * Retrieves the amount of references on the supplied file name by the article image m2m table.
     * Useful to determine if a file can be safely deleted or not
     * @param authorFileName The [FileEntry] name of the file that should be checked
     * @return The amount of references on that file from [ArticleAuthorImageJoin] by Articles that have a [Article.dateDownload]
     */
    suspend fun getDownloadedArticleAuthorReferenceCount(authorFileName: String): Int {
        return appDatabase.articleDao().getDownloadedArticleAuthorReferenceCount(authorFileName)
    }

    /**
     * Retrieves the amount of references on the supplied file name by the article image m2m table.
     * Useful to determine if a file can be safely deleted or not.
     * @param articleImageFileName The [FileEntry] name of the file that should be checked
     * @return The amount of references on that file from [ArticleImageJoin] by Articles that have a [Article.dateDownload]
     */
    suspend fun getDownloadedArticleImageReferenceCount(articleImageFileName: String): Int {
        return appDatabase.articleDao()
            .getDownloadedArticleImageReferenceCount(articleImageFileName)
    }
}