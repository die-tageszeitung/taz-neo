package de.taz.app.android.persistence.repository

import android.content.Context
import android.database.sqlite.SQLiteConstraintException
import androidx.lifecycle.*
import de.taz.app.android.annotation.Mockable
import de.taz.app.android.api.interfaces.ArticleOperations
import de.taz.app.android.api.models.*
import de.taz.app.android.persistence.join.ArticleAudioFileJoin
import de.taz.app.android.persistence.join.ArticleAuthorImageJoin
import de.taz.app.android.persistence.join.ArticleImageJoin
import de.taz.app.android.util.SingletonHolder
import kotlinx.coroutines.Dispatchers
import java.lang.Exception
import java.util.*

@Mockable
class ArticleRepository private constructor(applicationContext: Context) :
    RepositoryBase(applicationContext) {
    companion object : SingletonHolder<ArticleRepository, Context>(::ArticleRepository)

    private val fileEntryRepository = FileEntryRepository.getInstance(applicationContext)
    private val imageRepository = ImageRepository.getInstance(applicationContext)

    fun update(articleStub: ArticleStub) {
        appDatabase.articleDao().insertOrReplace(articleStub)
    }

    fun save(article: Article) {
        var articleToSave = article
        getStub(articleToSave.key)?.let {
            articleToSave = articleToSave.copy(bookmarked = it.bookmarked)
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
            author.imageAuthor?.let {
                fileEntryRepository.save(it)
                appDatabase.articleAuthorImageJoinDao().insertOrReplace(
                    ArticleAuthorImageJoin(articleFileName, author.name, it.name, index)
                )
            }
        }
    }

    fun get(articleFileName: String): Article? {
        return getStub(articleFileName)?.let {articleStubToArticle(it) }
    }

    fun getList(articleFileNames: List<String>): List<Article> {
        return getStubList(articleFileNames).map(this::articleStubToArticle)
    }

    fun getStub(articleFileName: String): ArticleStub? {
        return appDatabase.articleDao().get(articleFileName)
    }

    fun getStubList(articleFileNames: List<String>): List<ArticleStub> {
        return appDatabase.articleDao().get(articleFileNames)
    }

    fun getStubLiveData(articleName: String): LiveData<ArticleStub> {
        return appDatabase.articleDao().getLiveData(articleName)
    }

    fun getLiveData(articleName: String): LiveData<Article> {
        return appDatabase.articleDao().getLiveData(articleName).switchMap { articleStub ->
            liveData(Dispatchers.IO) {
                emit(articleStubToArticle(articleStub))
            }
        }
    }

    fun saveScrollingPosition(articleStub: ArticleStub, percentage: Int, position: Int) {
        val refreshedArticleStub = getStub(articleStub.articleFileName)
        if (refreshedArticleStub?.bookmarked == true) {
            log.debug("save scrolling position for article ${articleStub.articleFileName}")
            appDatabase.articleDao().update(
                articleStub.copy(percentage = percentage, position = position)
            )
        }
    }

    fun getSectionArticleStubListByArticleName(articleName: String): List<ArticleStub> {
        var articleStubList = appDatabase.articleDao().getSectionArticleListByArticle(articleName)
        // if it is the imprint we want to return a list of it
        if (articleStubList.isEmpty()) {
            articleStubList = getStub(articleName)?.let {
                listOf(it)
            } ?: emptyList()
        }
        return articleStubList
    }

    fun nextArticleStub(articleName: String): ArticleStub? {
        return appDatabase.sectionArticleJoinDao().getNextArticleStubInSection(articleName)
            ?: appDatabase.sectionArticleJoinDao().getNextArticleStubInNextSection(articleName)
    }


    fun previousArticleStub(articleName: String): ArticleStub? {
        return appDatabase.sectionArticleJoinDao().getPreviousArticleStubInSection(articleName)
            ?: appDatabase.sectionArticleJoinDao().getPreviousArticleStubInPreviousSection(
                articleName
            )
    }

    fun getImagesForArticle(articleFileName: String): List<Image> {
        return appDatabase.articleImageJoinDao().getImagesForArticle(articleFileName)
    }

    fun getAuthorImageFileNamesForArticle(articleFileName: String): List<String> {
        return appDatabase.articleAuthorImageJoinDao().getAuthorImageJoinForArticle(articleFileName)
            .mapNotNull { it.authorFileName }
    }

    @Throws(NotFoundException::class)
    fun articleStubToArticle(articleStub: ArticleStub): Article {
        val articleName = articleStub.articleFileName
        val articleHtml = fileEntryRepository.getOrThrow(articleName)
        val audioFile = appDatabase.articleAudioFileJoinDao().getAudioFileForArticle(articleName)
        val articleImages = appDatabase.articleImageJoinDao().getImagesForArticle(articleName)

        // get authors
        val authorImageJoins =
            appDatabase.articleAuthorImageJoinDao().getAuthorImageJoinForArticle(articleName)
        val authorImages = fileEntryRepository.getOrThrow(
            authorImageJoins
                .filter { !it.authorFileName.isNullOrEmpty() }
                .map { it.authorFileName!! }
        )

        val authors = authorImageJoins.map { authorImageJoin ->
            Author(
                authorImageJoin.authorName,
                authorImages.find { it.name == authorImageJoin.authorFileName })
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
            articleStub.articleType,
            articleStub.bookmarked,
            articleStub.position,
            articleStub.percentage,
            articleStub.dateDownload
        )
    }


    fun bookmarkArticle(article: Article) {
        bookmarkArticle(article.key)
    }

    fun bookmarkArticle(articleStub: ArticleStub) {
        bookmarkArticle(articleStub.articleFileName)
    }

    fun bookmarkArticle(articleName: String) {
        log.debug("bookmarked from article $articleName")
        getStub(articleName)?.copy(bookmarked = true)?.let {
            appDatabase.articleDao().update(it)
        }
    }

    fun debookmarkArticle(article: Article) {
        debookmarkArticle(article.key)
    }

    fun debookmarkArticle(articleStub: ArticleStub) {
        debookmarkArticle(articleStub.articleFileName)
    }

    fun debookmarkArticle(articleName: String) {
        log.debug("removed bookmark from article $articleName")
        getStub(articleName)?.copy(bookmarked = false)?.let {
            appDatabase.articleDao().update(it)
        }
    }

    fun getBookmarkedArticlesLiveData(): LiveData<List<Article>> =
        appDatabase.articleDao().getBookmarkedArticlesLiveData().switchMap { input ->
            liveData(Dispatchers.IO) {
                emit(input.map { articleStub -> articleStubToArticle(articleStub) })
            }
        }

    fun getBookmarkedArticleStubs(): List<ArticleStub> {
        return appDatabase.articleDao().getBookmarkedArticlesList()
    }

    fun getBookmarkedArticleStubsLiveData(): LiveData<List<ArticleStub>> {
        return appDatabase.articleDao().getBookmarkedArticlesLiveData()
    }

    fun isBookmarked(articleStub: ArticleStub): Boolean {
        return articleStub.bookmarked
    }

    fun isBookmarkedLiveData(articleName: String): LiveData<Boolean> {
        return getStubLiveData(articleName).map { it?.bookmarked ?: false }
    }

    fun getIndexInSection(articleName: String): Int {
        return appDatabase.sectionArticleJoinDao().getIndexOfArticleInSection(articleName).plus(1)
    }

    fun deleteArticle(article: Article) {
        appDatabase.articleDao().get(article.key)?.let {
            val articleStub = ArticleStub(article)
            if (!it.bookmarked) {
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
                    log.warn("article not deleted", e)
                }
            }
        }
    }

    fun isDownloadedLiveData(articleOperations: ArticleOperations) =
        isDownloadedLiveData(articleOperations.key)

    fun isDownloadedLiveData(articleFileName: String): LiveData<Boolean> {
        return appDatabase.articleDao().isDownloadedLiveData(articleFileName)
    }

    fun getArticleStubListForIssue(
        issueFeedName: String,
        issueDate: String,
        issueStatus: IssueStatus
    ): List<ArticleStub> {
        return appDatabase.articleDao()
            .getArticleStubListForIssue(issueFeedName, issueDate, issueStatus)
    }

    fun getArticleStubListForIssue(
        issueKey: IssueKey
    ): List<ArticleStub> {
        return appDatabase.articleDao()
            .getArticleStubListForIssue(issueKey.feedName, issueKey.date, issueKey.status)
    }

    fun getBookmarkedArticleStubListForIssuesAtDate(
        issueFeedName: String,
        issueDate: String
    ): List<ArticleStub> {
        return appDatabase.articleDao()
            .getBookmarkedArticleStubListForIssue(issueFeedName, issueDate)
    }


    fun setDownloadDate(
        articleStub: ArticleStub,
        date: Date?
    ) {
        update(articleStub.copy(dateDownload = date))
    }

    fun getDownloadDate(
        articleStub: ArticleStub
    ): Date? {
        return appDatabase.articleDao().getDownloadStatus(articleStub.articleFileName)
    }
}