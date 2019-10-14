package de.taz.app.android.persistence.repository

import android.content.Context
import androidx.lifecycle.LiveData
import de.taz.app.android.api.models.*
import de.taz.app.android.persistence.join.ArticleAudioFileJoin
import de.taz.app.android.persistence.join.ArticleAuthorImageJoin
import de.taz.app.android.persistence.join.ArticleImageJoin
import de.taz.app.android.util.SingletonHolder

class ArticleRepository private constructor(applicationContext: Context) :
    RepositoryBase(applicationContext) {
    companion object : SingletonHolder<ArticleRepository, Context>(::ArticleRepository)

    private val fileEntryRepository = FileEntryRepository.getInstance(applicationContext)

    fun save(article: Article) {
        appDatabase.runInTransaction {

            val articleFileName = article.articleHtml.name
            appDatabase.articleDao().insertOrReplace(ArticleBase(article))

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
    }

    fun getBase(articleName: String): ArticleBase {
        return appDatabase.articleDao().get(articleName)
    }

    @Throws(NotFoundException::class)
    fun getOrThrow(articleName: String): Article {
        return articleBaseToArticle(appDatabase.articleDao().get(articleName))
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

    fun nextArticleBase(articleName: String): ArticleBase? {
        return appDatabase.sectionArticleJoinDao().getNextArticleBaseInSection(articleName)
            ?: appDatabase.sectionArticleJoinDao().getNextArticleBaseInNextSection(articleName)
    }

    fun nextArticleBase(article: Article): ArticleBase? = nextArticleBase(article.articleFileName)

    fun previousArticleBase(articleName: String): ArticleBase? {
        return appDatabase.sectionArticleJoinDao().getPreviousArticleBaseInSection(articleName)
            ?: appDatabase.sectionArticleJoinDao().getPreviousArticleBaseInPreviousSection(
                articleName
            )
    }

    fun previousArticleBase(article: Article): ArticleBase? = previousArticleBase(article.articleFileName)

    fun nextArticle(articleName: String): Article? =
        nextArticleBase(articleName)?.let { articleBaseToArticle(it) }

    fun nextArticle(article: Article): Article? = nextArticle(article.articleFileName)

    fun previousArticle(articleName: String): Article? =
        previousArticleBase(articleName)?.let { articleBaseToArticle(it) }

    fun previousArticle(article: Article): Article? = previousArticle(article.articleFileName)

    @Throws(NotFoundException::class)
    fun articleBaseToArticle(articleBase: ArticleBase): Article {
        val articleName = articleBase.articleFileName
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
            articleBase.title,
            articleBase.teaser,
            articleBase.onlineLink,
            audioFile,
            articleBase.pageNameList,
            articleImages,
            authors
        )
    }

    fun bookmarkArticle(article: Article) {
        bookmarkArticle(ArticleBase(article))
    }

    fun bookmarkArticle(articleBase: ArticleBase) {
        appDatabase.articleDao().update(articleBase.copy(bookmarked = true))
    }

    fun bookmarkArticle(articleName: String) {
        bookmarkArticle(getBase(articleName))
    }

    fun debookmarkArticle(articleName: String) {
        debookmarkArticle(getBase(articleName))
    }

    fun debookmarkArticle(article: Article) {
        debookmarkArticle(ArticleBase(article))
    }

    fun debookmarkArticle(articleBase: ArticleBase) {
        appDatabase.articleDao().update(articleBase.copy(bookmarked = false))
    }

    fun getBookmarkedArticleBases(): LiveData<List<ArticleBase>> {
        return appDatabase.articleDao().getBookmarkedArticlesLiveData()
    }

    fun delete(article: Article) {
        appDatabase.runInTransaction {
            val articleFileName = article.articleHtml.name

            // delete authors
            article.authorList.forEachIndexed { index, author ->
                author.imageAuthor?.let {
                    appDatabase.articleAuthorImageJoinDao().delete(
                        ArticleAuthorImageJoin(articleFileName, author.name, it.name, index)
                    )
                    fileEntryRepository.delete(it)
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
            article.imageList.forEachIndexed { index, fileEntry ->
                appDatabase.articleImageJoinDao().delete(
                    ArticleImageJoin(articleFileName, fileEntry.name, index)
                )
            }

            article.imageList.forEach { image ->
                if (article.getSection()?.imageList?.contains(image) != true)
                    fileEntryRepository.delete(image)
            }

            appDatabase.articleDao().delete(ArticleBase(article))
        }
    }

}