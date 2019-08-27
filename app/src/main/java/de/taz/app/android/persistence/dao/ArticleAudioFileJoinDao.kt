package de.taz.app.android.persistence.dao

import androidx.room.*
import de.taz.app.android.api.models.Article
import de.taz.app.android.api.models.FileEntry
import de.taz.app.android.persistence.join.ArticleAudioFileJoin


@Dao
abstract class ArticleAudioFileJoinDao : BaseDao<ArticleAudioFileJoin>() {

    @Query(
        """SELECT * FROM FileEntry INNER JOIN ArticleAudioFile 
        ON FileEntry.name = ArticleAudioFile.audioFileName
        WHERE ArticleAudioFile.articleFileName == :articleFileName
    """
    )
    abstract fun getAudioFileForArticle(articleFileName: String): FileEntry?

    fun getAudioFileForArticle(article: Article): FileEntry? = article.audioFile?.name?.let {
        getAudioFileForArticle(it)
    }
}
