package de.taz.app.android.persistence.repository

import android.content.Context
import android.database.sqlite.SQLiteConstraintException
import androidx.lifecycle.LiveData
import androidx.room.withTransaction
import de.taz.app.android.api.models.Article
import de.taz.app.android.api.models.ArticleStub
import de.taz.app.android.api.models.ArticleStubWithSectionKey
import de.taz.app.android.api.models.Author
import de.taz.app.android.api.models.FileEntry
import de.taz.app.android.api.models.Image
import de.taz.app.android.persistence.join.ArticleAuthorImageJoin
import de.taz.app.android.persistence.join.ArticleImageJoin
import de.taz.app.android.util.SingletonHolder
import io.sentry.Sentry
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

    suspend fun save(article: Article) {
        var articleToSave = article
        getStub(articleToSave.key)?.let {
            articleToSave = articleToSave.copy(bookmarkedTime = it.bookmarkedTime)
        }

        val articleFileName = articleToSave.articleHtml.name

        // [ArticleStub.audioFileName] references the [AudioStub.fileName] as a ForeignKey,
        // thus the [AudioStub] must be saved before the [ArticleStub] to fulfill the constraint.
        articleToSave.audio?.let { audio ->
            audioRepository.save(audio)
        }

        appDatabase.articleDao().insertOrReplace(ArticleStub(articleToSave))

        // save html file
        fileEntryRepository.save(articleToSave.articleHtml)

        // save images and relations
        imageRepository.save(articleToSave.imageList)
        articleToSave.imageList.forEachIndexed { index, image ->
            appDatabase.articleImageJoinDao().insertOrReplace(
                ArticleImageJoin(articleFileName, image.name, index)
            )
        }

        // save authors
        articleToSave.authorList.forEachIndexed { index, author ->
//            TODO(peter) Check if it's okay to not have an image of author
            author.imageAuthor?.let { fileEntryRepository.save(it) }
            appDatabase.articleAuthorImageJoinDao().insertOrReplace(
                ArticleAuthorImageJoin(
                    articleFileName,
                    author.name,
                    author.imageAuthor?.name,
                    index
                )
            )
        }


    }

    suspend fun get(articleFileName: String): Article? {
        return getStub(articleFileName)?.let { articleStubToArticle(it) }
    }

    suspend fun getStub(articleFileName: String): ArticleStub? {
        return appDatabase.articleDao().get(articleFileName)
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

    suspend fun articleStubToArticle(articleStub: ArticleStub): Article {
        val articleName = articleStub.articleFileName
        val articleHtml = fileEntryRepository.getOrThrow(articleName)
        val articleImages = appDatabase.articleImageJoinDao().getImagesForArticle(articleName)
        val audio = articleStub.audioFileName?.let { audioRepository.get(it) }

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
            Sentry.captureException(nfe)
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
            articleStub.dateDownload
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

                // delete authors
                appDatabase.articleAuthorImageJoinDao().getAuthorImageJoinForArticle(
                    articleFileName
                ).forEach { articleAuthorImageJoin ->
                    val amountOfArticlesOfAuthor =
                        articleAuthorImageJoin.authorFileName?.let { author ->
                            appDatabase.articleAuthorImageJoinDao().getArticlesForAuthor(
                                author
                            )
                        }?.size ?: 2
                    appDatabase.articleAuthorImageJoinDao().delete(articleAuthorImageJoin)
                    if (amountOfArticlesOfAuthor == 1) {
                        articleAuthorImageJoin.authorFileName?.let { authorFileName ->
                            try {
                                fileEntryRepository.delete(
                                    fileEntryRepository.getOrThrow(
                                        authorFileName
                                    )
                                )
                            } catch (e: SQLiteConstraintException) {
                                // do nothing as author is still referenced by another article
                            } catch (e: NotFoundException) {
                                log.warn("tried to delete non-existent file: $authorFileName")
                            }
                        }
                    }
                }

                // delete html file
                fileEntryRepository.delete(article.articleHtml)

                // delete images and relations
                article.imageList.forEachIndexed { index, image ->
                    appDatabase.articleImageJoinDao().delete(
                        ArticleImageJoin(articleFileName, image.name, index)
                    )
                    try {
                        imageRepository.delete(image)
                    } catch (e: SQLiteConstraintException) {
                        // do not delete - still used by section/otherIssue/bookmarked article
                    }
                }

                try {
                    appDatabase.articleDao().delete(articleStub)
                } catch (e: Exception) {
                    log.warn(
                        "article ${articleStub.articleFileName} not deleted. Maybe it is an imprint used by another issue",
                        e
                    )
                    // if an issue has no imprint, it uses an imprint of an older issue. That is why it cannot be deleted here.
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
            .map { articleStubToArticle(it.articleStub) }

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