package de.taz.app.android.persistence.dao

import androidx.lifecycle.LiveData
import androidx.room.Dao
import androidx.room.Query
import de.taz.app.android.api.models.IssueStatus
import de.taz.app.android.api.models.IssueStub

@Dao
abstract class IssueDao: BaseDao<IssueStub>() {

    @Query("SELECT * FROM Issue WHERE feedName == :feedName AND date == :date AND status == :status ")
    abstract fun getByFeedAndDate(feedName: String, date: String, status: IssueStatus): IssueStub?

    @Query("SELECT * FROM Issue ORDER BY date DESC LIMIT 1")
    abstract fun getLatest(): IssueStub?

    @Query("SELECT * FROM Issue ORDER BY date DESC LIMIT 1")
    abstract fun getLatestLiveData(): LiveData<IssueStub?>

    @Query("SELECT * FROM Issue ORDER BY date DESC")
    abstract fun getAllLiveData(): LiveData<List<IssueStub>?>

    @Query("SELECT * FROM Issue WHERE Issue.status != \"public\" ORDER BY date DESC")
    abstract fun getAllLiveDataExceptPublic(): LiveData<List<IssueStub>?>

    @Query("SELECT * FROM Issue ORDER BY date DESC")
    abstract fun getAllIssueStubs(): List<IssueStub>

    @Query("SELECT * FROM Issue WHERE Issue.status == :status ORDER BY date DESC")
    abstract fun getIssueStubsByStatus(status: IssueStatus): List<IssueStub>

}
