package de.taz.app.android.persistence.dao

import androidx.room.Dao
import androidx.room.Query
import de.taz.app.android.api.models.AppInfo

@Dao
abstract class ArticleDao : BaseDao<AppInfo>() {
    @Query("SELECT * FROM AppInfo LIMIT 1")
    abstract fun get(): AppInfo
}
