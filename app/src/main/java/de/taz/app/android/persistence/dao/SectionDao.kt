package de.taz.app.android.persistence.dao

import androidx.room.Dao
import androidx.room.Query
import de.taz.app.android.api.models.SectionBase

@Dao
abstract class SectionDao : BaseDao<SectionBase>() {
    @Query("SELECT * FROM Section WHERE Section.sectionFileName == :fileName LIMIT 1")
    abstract fun get(fileName: String): SectionBase
}
