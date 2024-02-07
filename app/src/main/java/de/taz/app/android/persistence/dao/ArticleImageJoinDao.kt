package de.taz.app.android.persistence.dao

import androidx.room.Dao
import androidx.room.Query
import de.taz.app.android.api.models.Image
import de.taz.app.android.persistence.join.ArticleImageJoin


@Dao
interface ArticleImageJoinDao : BaseDao<ArticleImageJoin> {

    @Query(
        """
        SELECT name, storageType, moTime, sha256, size, folder, type, alpha, resolution, dateDownload, path, storageLocation
        FROM FileEntry INNER JOIN ArticleImageJoin
        ON FileEntry.name = ArticleImageJoin.imageFileName
        INNER JOIN Image ON Image.fileEntryName == ArticleImageJoin.imageFileName
        WHERE ArticleImageJoin.articleFileName == :articleFileName
        ORDER BY ArticleImageJoin.`index` ASC
    """
    )
    suspend fun getImagesForArticle(articleFileName: String): List<Image>

    @Query("DELETE FROM ArticleImageJoin WHERE articleFileName = :articleFileName")
    suspend fun deleteRelationToArticle(articleFileName: String)

}
