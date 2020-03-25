package de.taz.app.android.persistence.dao

import androidx.room.Dao
import androidx.room.Query
import de.taz.app.android.api.interfaces.SectionOperations
import de.taz.app.android.api.models.Image
import de.taz.app.android.persistence.join.SectionNavButtonJoin


@Dao
abstract class SectionNavButtonJoinDao : BaseDao<SectionNavButtonJoin>() {

    @Query(
        """SELECT Image.* FROM FileEntry INNER JOIN SectionNavButtonJoin
        ON Image.name = SectionNavButtonJoin.navButtonFileName
        WHERE SectionNavButtonJoin.sectionFileName == :sectionFileName
    """
    )
    abstract fun getNavButtonForSection(sectionFileName: String): Image

    @Query(
        """SELECT Image.name FROM FileEntry INNER JOIN SectionNavButtonJoin
        ON Image.name = SectionNavButtonJoin.navButtonFileName
        WHERE SectionNavButtonJoin.sectionFileName == :sectionFileName
    """
    )
    abstract fun getNavButtonNameForSection(sectionFileName: String): String

    fun getNavButtonForSectionOperation(section: SectionOperations): Image =
        getNavButtonForSection(section.key)

}
