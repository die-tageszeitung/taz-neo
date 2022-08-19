package de.taz.app.android.persistence.dao

import androidx.room.Dao
import androidx.room.Query
import de.taz.app.android.api.models.ViewerState

@Dao
interface ViewerStateDao : BaseDao<ViewerState> {
    @Query("""
        SELECT * FROM ViewerState WHERE displayableName = :displayableName
    """)
    suspend fun getByDisplayableName(displayableName: String): ViewerState?
}
