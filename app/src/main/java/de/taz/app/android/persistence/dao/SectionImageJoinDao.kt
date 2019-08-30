package de.taz.app.android.persistence.dao

import androidx.room.*
import de.taz.app.android.api.models.FileEntry
import de.taz.app.android.api.models.Section
import de.taz.app.android.api.models.SectionBase
import de.taz.app.android.persistence.join.SectionImageJoin


@Dao
abstract class SectionImageJoinDao : BaseDao<SectionImageJoin>() {

    @Query(
        """SELECT FileEntry.* FROM FileEntry INNER JOIN ArticleImageJoin
        ON FileEntry.name = ArticleImageJoin.imageFileName
        WHERE ArticleImageJoin.articleFileName == :sectionFileName
    """
    )
    abstract fun getImagesForSection(sectionFileName: String): List<FileEntry>?

    fun getImagesForSection(section: Section): List<FileEntry>? =
        getImagesForSection(section.sectionHtml.name)

    fun getImagesForSection(sectionBase: SectionBase): List<FileEntry>? =
        getImagesForSection(sectionBase.sectionFileName)
}
