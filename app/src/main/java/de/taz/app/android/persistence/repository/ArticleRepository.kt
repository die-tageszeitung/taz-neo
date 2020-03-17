package de.taz.app.android.persistence.repository

import android.content.Context
import android.database.sqlite.SQLiteConstraintException
import androidx.lifecycle.*
import de.taz.app.android.annotation.Mockable
import de.taz.app.android.api.models.*
import de.taz.app.android.persistence.join.ArticleAudioFileJoin
import de.taz.app.android.persistence.join.ArticleAuthorImageJoin
import de.taz.app.android.persistence.join.ArticleImageJoin
import de.taz.app.android.util.SingletonHolder
import kotlinx.coroutines.Dispatchers
import java.lang.Exception

@Mockable
class ArticleRepository private constructor(applicationContext: Context) :
    RepositoryBase(applicationContext) {
    companion object : SingletonHolder<ArticleRepository, Context>(::ArticleRepository)

    private val fileEntryRepository = FileEntryRepository.getInstance(applicationContext)

    fun save(article: Article) {
        val articleFileName = article.articleHtml.name
        appDatabase.articleDao().insertOrReplace(ArticleStub(article))

        // save audioFile and relation
        article.audioFile?.let { audioFile ->
            fileEntryRepository.save(audioFile)
            appDatabase.articleAudioFileJoinDao().insertOrReplace(
                ArticleAudioFileJoin(article.articleHtml.name, audioFile.name)
            )
        }

        // save html file
        fileEntryRepository.save(article.articleHtml)

        // save images and relations
        article.imageList.forEachIndexed { index, fileEntry ->
            fileEntryRepository.save(fileEntry)
            appDatabase.articleImageJoinDao().insertOrReplace(
                ArticleImageJoin(articleFileName, fileEntry.name, index)
            )
        }

        // save authors
        article.authorList.forEachIndexed { index, author ->
            author.imageAuthor?.let {
                fileEntryRepository.save(it)
                appDatabase.articleAuthorImageJoinDao().insertOrReplace(
                    ArticleAuthorImageJoin(articleFileName, author.name, it.name, index)
                )
            }
        }
    }

    fun getStub(articleName: String): ArticleStub? {
        return appDatabase.articleDao().get(articleName)
    }

    fun getStubLiveData(articleName: String): LiveData<ArticleStub?> {
        return appDatabase.articleDao().getLiveData(articleName)
    }

    @Throws(NotFoundException::class)
    fun getStubOrThrow(articleName: String): ArticleStub {
        return getStub(articleName) ?: throw NotFoundException()
    }

    @Throws(NotFoundException::class)
    fun getOrThrow(articleName: String): Article {
        return appDatabase.articleDao().get(articleName)?.let {
            articleStubToArticle(it)
        } ?: throw NotFoundException()
    }

    @Throws(NotFoundException::class)
    fun getOrThrow(articleNames: List<String>): List<Article> {
        return articleNames.map { getOrThrow(it) }
    }

    fun get(articleName: String): Article? {
        return try {
            getOrThrow(articleName)
        } catch (e: NotFoundException) {
            null
        }
    }

    fun getLiveData(articleName: String): LiveData<Article?> {
        return appDatabase.articleDao().getLiveData(articleName).switchMap { input ->
            liveData(Dispatchers.IO) {
                emit(input?.let { articleStubToArticle(input) })
            }
        }
    }

    fun nextArticleStub(articleName: String): ArticleStub? {
        return appDatabase.sectionArticleJoinDao().getNextArticleStubInSection(articleName)
            ?: appDatabase.sectionArticleJoinDao().getNextArticleStubInNextSection(articleName)
    }

    fun nextArticleStub(article: Article): ArticleStub? = nextArticleStub(article.articleFileName)

    fun previousArticleStub(articleName: String): ArticleStub? {
        return appDatabase.sectionArticleJoinDao().getPreviousArticleStubInSection(articleName)
            ?: appDatabase.sectionArticleJoinDao().getPreviousArticleStubInPreviousSection(
                articleName
            )
    }

    fun previousArticleStub(article: Article): ArticleStub? =
        previousArticleStub(article.articleFileName)

    fun nextArticle(articleName: String): Article? =
        nextArticleStub(articleName)?.let { articleStubToArticle(it) }

    fun nextArticle(article: Article): Article? = nextArticle(article.articleFileName)

    fun previousArticle(articleName: String): Article? =
        previousArticleStub(articleName)?.let { articleStubToArticle(it) }

    fun previousArticle(article: Article): Article? = previousArticle(article.articleFileName)

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
            articleStub.percentage
        )
    }

    fun bookmarkArticle(article: Article) {
        bookmarkArticle(ArticleStub(article))
    }

    fun bookmarkArticle(articleStub: ArticleStub) {
        log.debug("bookmarked from article ${articleStub.articleFileName}")
        appDatabase.articleDao().update(articleStub.copy(bookmarked = true))
    }

    @Throws(NotFoundException::class)
    fun bookmarkArticle(articleName: String) {
        bookmarkArticle(getStubOrThrow(articleName))
    }

    @Throws(NotFoundException::class)
    fun debookmarkArticle(articleName: String) {
        debookmarkArticle(getStubOrThrow(articleName))
    }

    fun debookmarkArticle(article: Article) {
        debookmarkArticle(ArticleStub(article))
    }

    fun debookmarkArticle(articleStub: ArticleStub) {
        log.debug("removed bookmark from article ${articleStub.articleFileName}")
        appDatabase.articleDao().update(articleStub.copy(bookmarked = false))
    }

    fun getBookmarkedArticles(): LiveData<List<Article>> =
        appDatabase.articleDao().getBookmarkedArticlesLiveData().switchMap { input ->
            liveData(Dispatchers.IO) {
                emit(input.map { articleStub -> articleStubToArticle(articleStub) })
            }
        }


    fun getBookmarkedArticlesList(): List<Article> {
        return appDatabase.articleDao().getBookmarkedArticlesList().map {
            articleStubToArticle(it)
        }
    }

    fun isBookmarked(article: Article): Boolean {
        return article.bookmarked
    }

    fun isBookmarked(articleStub: ArticleStub): Boolean {
        return articleStub.bookmarked
    }

    fun getIndexInSection(articleName: String): Int? {
        return appDatabase.sectionArticleJoinDao().getIndexOfArticleInSection(articleName)?.plus(1)
    }

    fun getIndexInSection(article: Article): Int? = getIndexInSection(article.articleFileName)

    fun saveScrollingPosition(article: Article, percentage: Int, position: Int) {
        saveScrollingPosition(ArticleStub(article), percentage, position)
    }

    fun saveScrollingPosition(articleStub: ArticleStub, percentage: Int, position: Int) {
        val articleStubLive = getStubOrThrow(articleStub.articleFileName)
        if (isBookmarked(articleStubLive)) {
            log.debug("save scrolling position for article ${articleStub.articleFileName}")
            appDatabase.articleDao()
                .update(articleStubLive.copy(percentage = percentage, position = position))
        }

    }

    fun delete(article: Article) {
        appDatabase.articleDao().get(article.articleFileName)?.let {
            val articleStub = ArticleStub(article)
            if (!it.bookmarked) {
                val articleFileName = article.articleHtml.name

                // delete authors
                appDatabase.articleAuthorImageJoinDao().getAuthorImageJoinForArticle(
                    articleFileName
                ).forEach { articleAuthorImageJoin ->
                    log.debug("deleting ArticleAuthor ${articleAuthorImageJoin.id}")
                    appDatabase.articleAuthorImageJoinDao().delete(articleAuthorImageJoin)
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

                // delete audioFile and relation
                article.audioFile?.let { audioFile ->
                    log.debug("deleting ArticleAudioFile ${audioFile.name}")
                    appDatabase.articleAudioFileJoinDao().delete(
                        ArticleAudioFileJoin(article.articleHtml.name, audioFile.name)
                    )
                    fileEntryRepository.delete(audioFile)
                }

                // delete html file
                fileEntryRepository.delete(article.articleHtml)

                // delete images and relations
                article.imageList.forEachIndexed { index, fileEntry ->
                    appDatabase.articleImageJoinDao().delete(
                        ArticleImageJoin(articleFileName, fileEntry.name, index)
                    )
                    log.debug("deleted ArticleImageJoin $articleFileName - ${fileEntry.name} - $index")
                    try {
                        fileEntryRepository.delete(fileEntry)
                        log.debug("deleted FileEntry of image ${fileEntry.name}")
                    } catch (e: SQLiteConstraintException) {
                        log.warn("FileEntry ${fileEntry.name} not deleted, maybe still used by section?")
                        // do not delete - still used by section
                    }
                }

                log.debug("delete ArticleStub $article")
                try {
                    appDatabase.articleDao().delete(articleStub)
                } catch (e: Exception) {
                    log.warn("article not deleted", e)
                }
            }
        }
    }
}