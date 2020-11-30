package de.taz.app.android.persistence.dao

import androidx.room.Dao
import androidx.room.Query
import de.taz.app.android.api.models.ViewerState

@Dao
abstract class ViewerStateDao : BaseDao<ViewerState>() {
    @Query("""
        SELECT * FROM ViewerState WHERE displayableName = :displayableName
    """)
    abstract fun getByDisplayableName(displayableName: String): ViewerState?
}
