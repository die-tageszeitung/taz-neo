package de.taz.app.android.persistence.repository

import androidx.room.Transaction
import de.taz.app.android.api.models.Article
import de.taz.app.android.api.models.ArticleBase
import de.taz.app.android.api.models.Author
import de.taz.app.android.persistence.AppDatabase
import de.taz.app.android.persistence.join.ArticleAudioFileJoin
import de.taz.app.android.persistence.join.ArticleAuthorImageJoin
import de.taz.app.android.persistence.join.ArticleImageJoin

class ArticleRepository(private val appDatabase: AppDatabase = AppDatabase.getInstance()) {


    @Transaction
    fun save(article: Article) {
        val articleFileName = article.articleHtml.name
        appDatabase.articleDao().insertOrReplace(ArticleBase(article))

        // save audioFile and relation
        article.audioFile?.let { audioFile ->
            appDatabase.fileEntryDao().insertOrReplace(audioFile)
            appDatabase.articleAudioFileJoinDao().insertOrReplace(
                ArticleAudioFileJoin(article.articleHtml.name, audioFile.name)
            )
        }

        // save html file
        appDatabase.fileEntryDao().insertOrReplace(article.articleHtml)

        // save images and relations
        appDatabase.fileEntryDao().insertOrReplace(article.imageList)
        appDatabase.articleImageJoinDao().insertOrReplace(
            article.imageList.map { ArticleImageJoin(articleFileName, it.name) }
        )

        // save authors
        appDatabase.articleAuthorImageJoinDao().insertOrReplace(
            article.authorList.map { author ->
                ArticleAuthorImageJoin(articleFileName, author.name, author.imageAuthor?.name)
            }
        )
    }

    fun getBase(articleName: String): ArticleBase {
        return appDatabase.articleDao().get(articleName)
    }


    fun get(articleName: String): Article {
        val articleBase = appDatabase.articleDao().get(articleName)
        val articleHtml = appDatabase.fileEntryDao().getByName(articleName)
        val audioFile = appDatabase.articleAudioFileJoinDao().getAudioFileForArticle(articleName)
        val articleImages = appDatabase.articleImageJoinDao().getImagesForArticle(articleName).reversed() // TODO replace reversed with override equals?

        // get authors
        val authorImageJoins =
            appDatabase.articleAuthorImageJoinDao().getAuthorImageJoinForArticle(articleName)
        val authorImages = appDatabase.fileEntryDao().getByNames(
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

    fun get(articleNames: List<String>): List<Article> {
        return articleNames.map { get(it) }
    }

}