package de.taz.app.android.persistence.dao

import androidx.lifecycle.LiveData
import androidx.room.Dao
import androidx.room.Query
import de.taz.app.android.api.models.IssueStub

@Dao
abstract class IssueDao: BaseDao<IssueStub>() {

    @Query("SELECT * FROM Issue WHERE feedName == :feedName AND date == :date")
    abstract fun getByFeedAndDate(feedName: String, date: String): IssueStub?

    @Query("SELECT * FROM Issue ORDER BY date DESC LIMIT 1")
    abstract fun getLatest(): IssueStub?

    @Query("SELECT * FROM Issue ORDER BY date DESC LIMIT 1")
    abstract fun getLatestLiveData(): LiveData<IssueStub?>

    @Query("SELECT * FROM Issue ORDER BY date")
    abstract fun getAllLiveData(): LiveData<List<IssueStub>?>

}
