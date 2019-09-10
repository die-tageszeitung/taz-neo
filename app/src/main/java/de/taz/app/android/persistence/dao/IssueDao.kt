package de.taz.app.android.persistence.dao

import androidx.room.Dao
import androidx.room.Query
import de.taz.app.android.api.models.IssueBase

@Dao
abstract class IssueDao: BaseDao<IssueBase>() {

    @Query("SELECT * FROM Issue WHERE feedName == :feedName AND date == :date")
    abstract fun getByFeedAndDate(feedName: String, date: String): IssueBase

    @Query("SELECT * FROM Issue ORDER BY date DESC LIMIT 1")
    abstract fun getLatest(): IssueBase?

}
