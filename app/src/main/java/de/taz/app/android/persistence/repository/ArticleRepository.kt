package de.taz.app.android.persistence.repository

import androidx.room.Transaction
import de.taz.app.android.api.models.Article
import de.taz.app.android.api.models.ArticleBase
import de.taz.app.android.api.models.Author
import de.taz.app.android.persistence.AppDatabase
import de.taz.app.android.persistence.join.ArticleAudioFileJoin
import de.taz.app.android.persistence.join.ArticleAuthorImageJoin
import de.taz.app.android.persistence.join.ArticleImageJoin
import kotlin.Exception

class ArticleRepository(private val appDatabase: AppDatabase = AppDatabase.getInstance()) {

    private val fileEntryRepository = FileEntryRepository(appDatabase)

    @Transaction
    fun save(article: Article) {
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
        article.imageList.forEach {
            fileEntryRepository.save(it)
            appDatabase.articleImageJoinDao().insertOrReplace(
               ArticleImageJoin(articleFileName, it.name)
            )
        }

        // save authors
        article.authorList.forEach { author ->
            author.imageAuthor?.let {
                fileEntryRepository.save(it)
                appDatabase.articleAuthorImageJoinDao().insertOrReplace(
                    ArticleAuthorImageJoin(articleFileName, author.name, it.name)
                )
            }
        }
    }

    fun getBase(articleName: String): ArticleBase {
        return appDatabase.articleDao().get(articleName)
    }

    @Throws(NotFoundException::class)
    fun getOrThrow(articleName: String): Article {
        val articleBase = appDatabase.articleDao().get(articleName)
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
            Author(authorImageJoin.authorName, authorImages.find { it.name == authorImageJoin.authorFileName })
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

    @Throws(NotFoundException::class)
    fun getOrThrow(articleNames: List<String>) : List<Article> {
        return articleNames.map { getOrThrow(it) }
    }

    fun get(articleName: String): Article? {
        return try {
            getOrThrow(articleName)
        } catch (e: NotFoundException) {
            null
        }
    }

}