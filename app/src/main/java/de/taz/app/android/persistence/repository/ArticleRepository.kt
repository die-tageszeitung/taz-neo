package de.taz.app.android.persistence.repository

import android.content.Context
import android.database.sqlite.SQLiteConstraintException
import androidx.lifecycle.LiveData
import androidx.lifecycle.liveData
import androidx.lifecycle.map
import androidx.lifecycle.switchMap
import de.taz.app.android.annotation.Mockable
import de.taz.app.android.api.interfaces.ArticleOperations
import de.taz.app.android.api.models.*
import de.taz.app.android.persistence.cache.IssueInMemoryCache
import de.taz.app.android.persistence.join.ArticleAudioFileJoin
import de.taz.app.android.persistence.join.ArticleAuthorImageJoin
import de.taz.app.android.persistence.join.ArticleImageJoin
import de.taz.app.android.util.SingletonHolder
import io.sentry.Sentry
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.util.*

@Mockable
class ArticleRepository private constructor(applicationContext: Context) :
    RepositoryBase(applicationContext) {
    companion object : SingletonHolder<ArticleRepository, Context>(::ArticleRepository)

    private val fileEntryRepository = FileEntryRepository.getInstance(applicationContext)
    private val imageRepository = ImageRepository.getInstance(applicationContext)
    private val issueCache = IssueInMemoryCache.getInstance(Unit)

    suspend fun update(articleStub: ArticleStub) {
        appDatabase.articleDao().insertOrReplace(articleStub)
        issueCache.invalidate(IssuePublication(articleStub))
    }

    suspend fun save(article: Article) {
        var articleToSave = article
        getStub(articleToSave.key)?.let {
            articleToSave = articleToSave.copy(bookmarkedTime = it.bookmarkedTime)
        }

        val articleFileName = articleToSave.articleHtml.name
        appDatabase.articleDao().insertOrReplace(ArticleStub(articleToSave))

        // save audioFile and relation
        articleToSave.audioFile?.let { audioFile ->
            fileEntryRepository.save(audioFile)
            appDatabase.articleAudioFileJoinDao().insertOrReplace(
                ArticleAudioFileJoin(articleToSave.articleHtml.name, audioFile.name)
            )
        }

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

        issueCache.invalidate(IssuePublication(article))
    }

    suspend fun get(articleFileName: String): Article? {
        return getStub(articleFileName)?.let { articleStubToArticle(it) }
    }

    suspend fun getStub(articleFileName: String): ArticleStub? {
        return appDatabase.articleDao().get(articleFileName)
    }

    fun getStubLiveData(articleName: String): LiveData<ArticleStub?> {
        return appDatabase.articleDao().getLiveData(articleName)
    }

    fun getLiveData(articleName: String): LiveData<Article?> {
        return appDatabase.articleDao().getLiveData(articleName).switchMap { articleStub ->
            liveData {
                emit(articleStub?.let { articleStubToArticle(it) })
            }
        }
    }

    suspend fun saveScrollingPosition(articleFileName: String, percentage: Int, position: Int) {
        val articleStub = getStub(articleFileName)
        if (articleStub?.bookmarkedTime != null) {
            log.debug("save scrolling position for article ${articleStub.articleFileName}")
            appDatabase.articleDao().update(
                articleStub.copy(percentage = percentage, position = position)
            )
            issueCache.updateArticle(articleStub)
        }
    }

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
        val audioFile = appDatabase.articleAudioFileJoinDao().getAudioFileForArticle(articleName)
        val articleImages = appDatabase.articleImageJoinDao().getImagesForArticle(articleName)

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
            log.warn(hint)
            Sentry.captureException(nfe, hint)
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
            audioFile,
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


    suspend fun bookmarkArticle(article: Article) {
        bookmarkArticle(article.key)
    }

    suspend fun bookmarkArticle(articleStub: ArticleStub) {
        bookmarkArticle(articleStub.articleFileName)
    }

    suspend fun bookmarkArticle(articleName: String) {
        val currentDate = Date()
        getStub(articleName)?.copy(bookmarkedTime = currentDate)?.let {
            appDatabase.articleDao().update(it)
            issueCache.updateArticle(it)
        }
    }

    suspend fun setBookmarkedTime(articleName: String, date: Date) {
        getStub(articleName)?.copy(bookmarkedTime = date)?.let {
            appDatabase.articleDao().update(it)
            issueCache.updateArticle(it)
        }
    }

    suspend fun debookmarkArticle(article: Article) {
        debookmarkArticle(article.key)
    }

    suspend fun debookmarkArticle(articleStub: ArticleStub) {
        debookmarkArticle(articleStub.articleFileName)
    }

    suspend fun debookmarkArticle(articleName: String) {
        log.debug("removed bookmark from article $articleName")
        getStub(articleName)?.copy(bookmarkedTime = null)?.let {
            appDatabase.articleDao().update(it)
            issueCache.updateArticle(it)
        }
    }

    fun getBookmarkedArticlesFlow(): Flow<List<Article>> =
        appDatabase.articleDao().getBookmarkedArticlesFlow().map { articles ->
            articles.map { articleStubToArticle(it) }
        }

    suspend fun getBookmarkedArticleStubs(): List<ArticleStub> {
        return appDatabase.articleDao().getBookmarkedArticlesFlow().first()
    }

    fun getBookmarkedArticleStubsFlow(): Flow<List<ArticleStub>> {
        return appDatabase.articleDao().getBookmarkedArticlesFlow()
    }

    fun isBookmarkedLiveData(articleName: String): LiveData<Boolean> {
        return getStubLiveData(articleName).map { it?.bookmarkedTime != null }
    }

    fun getBookmarkedArticleStubsForIssue(issueKey: IssueKey): Flow<List<ArticleStub>> {
        return appDatabase.articleDao().getBookmarkedArticleStubsForIssue(issueKey.feedName, issueKey.date, issueKey.status)
    }

    suspend fun getIndexInSection(articleName: String): Int {
        return appDatabase.sectionArticleJoinDao().getIndexOfArticleInSection(articleName).plus(1)
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

                // delete audioFile and relation
                article.audioFile?.let { audioFile ->
                    appDatabase.articleAudioFileJoinDao().delete(
                        ArticleAudioFileJoin(article.articleHtml.name, audioFile.name)
                    )
                    fileEntryRepository.delete(audioFile)
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
            }
            issueCache.invalidate(IssuePublication(articleStub))
        }
    }

    suspend fun isDownloadedLiveData(articleOperations: ArticleOperations) =
        isDownloadedLiveData(articleOperations.key)

    suspend fun isDownloadedLiveData(articleFileName: String): LiveData<Boolean> {
        return appDatabase.articleDao().isDownloadedLiveData(articleFileName)
    }

    suspend fun getArticleStubListForIssue(
        issueKey: IssueKey
    ): List<ArticleStubWithSectionKey> {
        val articleStubList = appDatabase.articleDao()
            .getArticleStubListForIssue(issueKey.feedName, issueKey.date, issueKey.status)
        val sectionArticleJoinList = appDatabase.sectionArticleJoinDao().getSectionArticleJoinsForIssue(issueKey.feedName, issueKey.date, issueKey.status)
        val articleSectionMap = sectionArticleJoinList.associate { it.articleFileName to it.sectionFileName }

        return articleStubList.map {
            val sectionKey = requireNotNull(articleSectionMap[it.articleFileName])
            ArticleStubWithSectionKey(it, sectionKey)
        }
    }

    suspend fun setDownloadDate(
        articleStub: ArticleStub,
        date: Date?
    ) {
        // Note, that we are invalidating the issue cache when calling update() with the dateDownloaded.
        // While this is quite broad it prevents us from having outdated FileEntry information on the Issues Article instances
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

    fun hasAudioLiveData(articleFileName: String): LiveData<Boolean> {
        return appDatabase.articleAudioFileJoinDao().hasAudioFile(articleFileName)
    }
}