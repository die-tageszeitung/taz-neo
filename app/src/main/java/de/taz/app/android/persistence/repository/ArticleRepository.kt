package de.taz.app.android.persistence.repository

import android.content.Context
import android.database.sqlite.SQLiteConstraintException
import de.taz.app.android.api.models.Article
import de.taz.app.android.api.models.ArticleStub
import de.taz.app.android.api.models.ArticleStubWithSectionKey
import de.taz.app.android.api.models.Author
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
        getStub(articleToSave.key)?.let {
            articleToSave = articleToSave.copy(bookmarkedTime = it.bookmarkedTime)
        }

        val articleFileName = articleToSave.articleHtml.name

        // [ArticleStub.audioFileName] references the [AudioStub.fileName] as a ForeignKey,
        // thus the [AudioStub] must be saved before the [ArticleStub] to fulfill the constraint.
        articleToSave.audio?.let { audio ->
            audioRepository.saveInternal(audio)
        }

        articleToSave.pdf?.let { pdf ->
            fileEntryRepository.save(pdf)
        }

        appDatabase.articleDao().insertOrReplace(ArticleStub(articleToSave))

        // save html file
        fileEntryRepository.save(articleToSave.articleHtml)

        // save images and relations
        imageRepository.saveInternal(articleToSave.imageList)
        appDatabase.articleImageJoinDao().apply {
            deleteRelationToArticle(articleFileName)
            articleToSave.imageList.forEachIndexed { index, image ->
                insertOrReplace(ArticleImageJoin(articleFileName, image.name, index))
            }
        }

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
        return getStub(articleFileName)?.let { articleStubToArticle(it) }
    }

    suspend fun getStub(articleFileName: String): ArticleStub? {
        return appDatabase.articleDao().get(articleFileName)
    }

    suspend fun getStubByMediaSyncId(articleMediaSyncId: Int): ArticleStub? {
        return appDatabase.articleDao().getByMediaSyncId(articleMediaSyncId)
    }

    /* currently not used. see [TazApiJS] for further information
    suspend fun saveScrollingPosition(articleFileName: String, percentage: Int, position: Int) {
        val articleStub = getStub(articleFileName)
        if (articleStub?.bookmarkedTime != null) {
            log.debug("save scrolling position for article ${articleStub.articleFileName}")
            appDatabase.articleDao().update(
                articleStub.copy(percentage = percentage, position = position)
            )
        }
    }
    */

    suspend fun getSectionArticleStubListByArticleName(articleName: String): List<ArticleStub> {
        var articleStubList = appDatabase.articleDao().getSectionArticleListByArticle(articleName)
        // if it is the imprint we want to return a list of it
        if (articleStubList.isEmpty()) {
            articleStubList = getStub(articleName)?.let {
                listOf(it)
            } ?: emptyList()
        }
        return articleStubList
    }

    suspend fun nextArticleStub(articleName: String): ArticleStub? {
        return appDatabase.sectionArticleJoinDao().getNextArticleStubInSection(articleName)
            ?: appDatabase.sectionArticleJoinDao().getNextArticleStubInNextSection(articleName)
    }


    suspend fun previousArticleStub(articleName: String): ArticleStub? {
        return appDatabase.sectionArticleJoinDao().getPreviousArticleStubInSection(articleName)
            ?: appDatabase.sectionArticleJoinDao().getPreviousArticleStubInPreviousSection(
                articleName
            )
    }

    suspend fun getImagesForArticle(articleFileName: String): List<Image> {
        return appDatabase.articleImageJoinDao().getImagesForArticle(articleFileName)
    }

    suspend fun articleStubToArticle(articleStub: ArticleStub): Article? {
        return try {
            articleStubToArticleOrThrow(articleStub)
        } catch (e: NotFoundException) {
            log.error("Could not get Article (${articleStub.articleFileName}), because some critical data can't be loaded.", e)
            null
        }
    }

    suspend fun getFileNamesForArticle(articleName: String): List<String> {
        val list = mutableListOf(articleName)
        list.addAll(appDatabase.articleImageJoinDao().getNormalImageFileNamesForArticle(articleName))

        // get authors
        val authorImageJoins =
            appDatabase.articleAuthorImageJoinDao().getAuthorImageJoinForArticle(articleName)

        val authorImageNames = authorImageJoins
                .filter { !it.authorFileName.isNullOrEmpty() }
                .mapNotNull { it.authorFileName }

        list.addAll(authorImageNames)
        return list.distinct()
    }

    suspend fun getAuthorNamesForArticle(articleName: String): List<String> {
        val authorImageJoins =
            appDatabase.articleAuthorImageJoinDao().getAuthorImageJoinForArticle(articleName)

        return authorImageJoins.mapNotNull { it.authorName }.distinct()
    }


    /**
     * Tries convert the ArticleStub to an Article
     * or throws an [NotFoundException] if some of the critical data can't be loaded.
     */
    private suspend fun articleStubToArticleOrThrow(articleStub: ArticleStub): Article {
        val articleName = articleStub.articleFileName
        val articleHtml = fileEntryRepository.getOrThrow(articleName)
        val articleImages = appDatabase.articleImageJoinDao().getImagesForArticle(articleName)
        val audio = articleStub.audioFileName?.let { audioRepository.get(it) }
        val articlePdf = articleStub.pdfFileName?.let { fileEntryRepository.get(it) }

        // get authors
        val authorImageJoins =
            appDatabase.articleAuthorImageJoinDao().getAuthorImageJoinForArticle(articleName)

        val authorImages = try {
            fileEntryRepository.getOrThrow(
                authorImageJoins
                    .filter { !it.authorFileName.isNullOrEmpty() }
                    .map { it.authorFileName!! }
            )
        } catch (nfe: NotFoundException) {
            val hint = "fileEntryRepository could not find fileEntries for authorImages: " +
                    "${
                        authorImageJoins
                            .filter { !it.authorFileName.isNullOrEmpty() }
                            .map { it.authorFileName!! }
                    }"
            log.warn(hint, nfe)
            SentryWrapper.captureException(nfe)
            null
        }

        val authors = authorImageJoins.map { authorImageJoin ->
            Author(
                authorImageJoin.authorName,
                authorImages?.find { it.name == authorImageJoin.authorFileName })
        }

        return Article(
            articleHtml,
            articleStub.issueFeedName,
            articleStub.issueDate,
            articleStub.title,
            articleStub.teaser,
            articleStub.onlineLink,
            audio,
            articleStub.pageNameList,
            articleImages,
            authors,
            articleStub.mediaSyncId,
            articleStub.chars,
            articleStub.words,
            articleStub.readMinutes,
            articleStub.articleType,
            articleStub.bookmarkedTime,
            articleStub.position,
            articleStub.percentage,
            articleStub.dateDownload,
            articlePdf,
        )
    }

    suspend fun getIndexInSection(articleName: String): Int? {
        return appDatabase.sectionArticleJoinDao().getIndexOfArticleInSection(articleName)?.plus(1)
    }

    suspend fun deleteArticle(article: Article) {
        appDatabase.articleDao().get(article.key)?.let {
            val articleStub = ArticleStub(article)
            if (it.bookmarkedTime == null) {
                val articleFileName = article.articleHtml.name

                // Delete all author relations of this Article,
                // but keep the actual FileEntries and rely on the Scrubber to clean them later
                appDatabase.articleAuthorImageJoinDao().deleteRelationToArticle(articleFileName)

                // delete html file
                fileEntryRepository.delete(article.articleHtml)

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

    suspend fun getArticleStubListForIssue(
        issueKey: IssueKey
    ): List<ArticleStubWithSectionKey> {
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
            ArticleStubWithSectionKey(it, sectionKey)
        }
    }

    suspend fun getArticleListForIssue(issueKey: IssueKey): List<Article> =
        getArticleStubListForIssue(issueKey)
            .mapNotNull { articleStubToArticle(it.articleStub) }

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