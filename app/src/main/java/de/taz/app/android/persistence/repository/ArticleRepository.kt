package de.taz.app.android.persistence.repository

import androidx.room.Transaction
import de.taz.app.android.api.models.*
import de.taz.app.android.persistence.AppDatabase
import de.taz.app.android.persistence.join.ArticleAudioFileJoin
import de.taz.app.android.persistence.join.ArticleAuthorImageJoin
import de.taz.app.android.persistence.join.ArticleImageJoin

object ArticleRepository {

    private val appDatabase = AppDatabase.getInstance()

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
        article.imageList?.let { imageList ->
            appDatabase.fileEntryDao().insertOrReplace(imageList)
            appDatabase.articleImageJoinDao().insertOrReplace(
                article.imageList.map { ArticleImageJoin(articleFileName, it.name) }
            )
        }

        // save authors
        article.authorList?.let {
            appDatabase.articleAuthorImageJoinDao().insertOrReplace(
                it.map { author ->
                    ArticleAuthorImageJoin(articleFileName, author.name, author.imageAuthor?.name)
                }
            )
        }
    }

    fun getBase(articleName: String): ArticleBase {
        return appDatabase.articleDao().get(articleName)
    }


    fun get(articleName: String): Article {
        val articleBase = appDatabase.articleDao().get(articleName)
        val articleHtml = appDatabase.fileEntryDao().getByName(articleName)
        val audioFile = appDatabase.articleAudioFileJoinDao().getAudioFileForArticle(articleName)
        val articleImages = appDatabase.articleImageJoinDao().getImagesForArticle(articleName)

        // get authors
        val authorImageJoins =
            appDatabase.articleAuthorImageJoinDao().getAuthorImageJoinForArticle(articleName)
        val authorImages = appDatabase.fileEntryDao().getByNames(
            authorImageJoins
                ?.filter { !it.authorFileName.isNullOrEmpty() }
                ?.map { it.authorFileName!! } ?: listOf()
        )

        val authors = authorImageJoins?.map { authorImageJoin ->
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

}