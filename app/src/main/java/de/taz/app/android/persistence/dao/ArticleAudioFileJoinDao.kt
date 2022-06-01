package de.taz.app.android.persistence.dao

import androidx.lifecycle.LiveData
import androidx.room.Dao
import androidx.room.Query
import de.taz.app.android.api.models.Article
import de.taz.app.android.api.models.FileEntry
import de.taz.app.android.persistence.join.ArticleAudioFileJoin


@Dao
abstract class ArticleAudioFileJoinDao : BaseDao<ArticleAudioFileJoin>() {

    @Query(
        """SELECT FileEntry.* FROM FileEntry INNER JOIN ArticleAudioFileJoin
        ON FileEntry.name = ArticleAudioFileJoin.audioFileName
        WHERE ArticleAudioFileJoin.articleFileName == :articleFileName
    """
    )
    abstract fun getAudioFileForArticle(articleFileName: String): FileEntry?

    fun getAudioFileForArticle(article: Article): FileEntry? = article.audioFile?.name?.let {
        getAudioFileForArticle(it)
    }

    @Query("SELECT EXISTS(SELECT * FROM ArticleAudioFileJoin where articleFileName = :articleFileName)")
    abstract fun hasAudioFile(articleFileName: String): LiveData<Boolean>
}
