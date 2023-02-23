package de.taz.app.android.persistence.dao

import androidx.lifecycle.LiveData
import androidx.room.Dao
import androidx.room.Query
import de.taz.app.android.api.models.FileEntry
import de.taz.app.android.persistence.join.ArticleAudioFileJoin


@Dao
interface ArticleAudioFileJoinDao : BaseDao<ArticleAudioFileJoin> {

    @Query(
        """SELECT FileEntry.* FROM FileEntry INNER JOIN ArticleAudioFileJoin
        ON FileEntry.name = ArticleAudioFileJoin.audioFileName
        WHERE ArticleAudioFileJoin.articleFileName == :articleFileName
    """
    )
    suspend fun getAudioFileForArticle(articleFileName: String): FileEntry?
}
