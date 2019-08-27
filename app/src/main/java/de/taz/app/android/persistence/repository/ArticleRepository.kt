package de.taz.app.android.persistence.repository

import androidx.room.Transaction
import de.taz.app.android.api.models.*
import de.taz.app.android.persistence.AppDatabase
import de.taz.app.android.persistence.join.ArticleAudioFileJoin
import de.taz.app.android.persistence.join.ArticleImageJoin

object ArticleRepository {

    private val appDatabase = AppDatabase.getInstance()

    @Transaction
    fun save(article: Article) {
        val articleName = article.articleHtml.name
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
                article.imageList.map { ArticleImageJoin(articleName, it.name) }
            )
        }

        // TODO save  authors
    }

    fun getBase(articleName: String): ArticleBase {
        return appDatabase.articleDao().get(articleName)
    }


    fun get(articleName: String): Article {
        val articleBase = appDatabase.articleDao().get(articleName)
        val articleHtml = appDatabase.fileEntryDao().getByName(articleName)
        val audioFile = appDatabase.articleAudioFileJoinDao().getAudioFileForArticle(articleName)
        val articleImages = appDatabase.articleImageJoinDao().getImagesForArticle(articleName)
        val authors = null // TODO load authors
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