package de.taz.app.android.persistence.dao

import androidx.lifecycle.LiveData
import androidx.room.Dao
import androidx.room.Query
import de.taz.app.android.api.models.IssueStatus
import de.taz.app.android.api.models.IssueStub
import java.util.*

@Dao
abstract class IssueDao : BaseDao<IssueStub>() {

    @Query("SELECT * FROM Issue WHERE feedName == :feedName AND date == :date AND status == :status ")
    abstract fun getByFeedDateAndStatus(
        feedName: String,
        date: String,
        status: IssueStatus
    ): IssueStub?

    @Query("SELECT * FROM Issue WHERE feedName == :feedName AND strftime('%s', date) <= strftime('%s', :date) AND status == :status ORDER BY date DESC LIMIT 1 ")
    abstract fun getLatestByFeedDateAndStatus(
        feedName: String,
        date: String,
        status: IssueStatus
    ): IssueStub?

    @Query("SELECT * FROM Issue WHERE strftime('%s', date) <= strftime('%s', :fromDate) AND feedName IN (:feedNames) AND status == :status  ORDER BY date DESC LIMIT :limit")
    abstract fun getIssuesFromDateByFeed(fromDate: String, feedNames: List<String>, status: IssueStatus, limit: Int): List<IssueStub>
    
    @Query("SELECT * FROM Issue WHERE strftime('%s', date) <= strftime('%s', :date) ORDER BY date DESC LIMIT 1 ")
    abstract fun getLatestByDate(date: String): IssueStub?

    @Query("SELECT * FROM Issue WHERE strftime('%s', date) <= strftime('%s', :date) AND Issue.status == 'regular' ORDER BY date DESC LIMIT 1 ")
    abstract fun getLatestRegularByDate(date: String): IssueStub?

    @Query("SELECT * FROM Issue WHERE feedName == :feedName AND date == :date AND status == :status ")
    abstract fun getByFeedDateAndStatusLiveData(
        feedName: String,
        date: String,
        status: IssueStatus
    ): LiveData<IssueStub?>

    @Query("SELECT * FROM Issue ORDER BY date DESC LIMIT 1")
    abstract fun getLatest(): IssueStub?

    @Query("SELECT * FROM Issue WHERE Issue.status == :status AND Issue.feedName IN (:feedName) ORDER BY date DESC LIMIT 1")
    abstract fun getLatestByFeedAndStatus(status: IssueStatus, feedName: List<String>): IssueStub?

    @Query("SELECT * FROM Issue ORDER BY date DESC LIMIT 1")
    abstract fun getLatestLiveData(): LiveData<IssueStub?>

    @Query("SELECT * FROM Issue ORDER BY date DESC")
    abstract fun getAllLiveData(): LiveData<List<IssueStub>>

    @Query("SELECT * FROM Issue WHERE Issue.status != \"public\" ORDER BY date DESC")
    abstract fun getAllLiveDataExceptPublic(): LiveData<List<IssueStub>?>

    @Query("SELECT * FROM Issue ORDER BY date DESC")
    abstract fun getAllIssueStubs(): List<IssueStub>

    @Query("SELECT * FROM Issue WHERE Issue.status == :status ORDER BY date DESC")
    abstract fun getIssueStubsByStatus(status: IssueStatus): List<IssueStub>

    @Query("SELECT * FROM Issue WHERE dateDownload IS NOT NULL ORDER BY dateDownload ASC LIMIT 1")
    abstract fun getEarliestDownloaded(): IssueStub?

    @Query("SELECT * FROM Issue ORDER BY date ASC LIMIT 1")
    abstract fun getEarliest(): IssueStub?

    @Query("SELECT * FROM Issue WHERE dateDownload IS NOT NULL")
    abstract fun getAllDownloaded(): List<IssueStub>

    @Query("SELECT * FROM Issue WHERE dateDownload IS NOT NULL")
    abstract fun getAllDownloadedLiveData(): LiveData<List<IssueStub>?>

    @Query("SELECT COUNT(date) FROM Issue WHERE dateDownload IS NOT NULL")
    abstract fun getDownloadedIssuesCountLiveData(): LiveData<Int>

    @Query("SELECT EXISTS (SELECT * FROM Issue WHERE dateDownload IS NOT NULL AND feedName == :feedName AND date == :date AND status == :status)")
    abstract fun isDownloadedLiveData(
        feedName: String,
        date: String,
        status: IssueStatus
    ): LiveData<Boolean>

    @Query(
        """
       SELECT Issue.* FROM Issue
        INNER JOIN ArticleImageJoin
        INNER JOIN SectionImageJoin
        INNER JOIN SectionArticleJoin
        INNER JOIN IssueSectionJoin
         WHERE ArticleImageJoin.imageFileName == :imageName AND ArticleImageJoin.articleFileName == SectionArticleJoin.articleFileName AND IssueSectionJoin.sectionFileName == SectionArticleJoin.sectionFileName
        AND IssueSectionJoin.issueDate == Issue.date AND IssueSectionJoin.issueFeedName == Issue.feedName AND IssueSectionJoin.issueStatus == Issue.status
    """
    )
    abstract fun getStubForArticleImageName(imageName: String): IssueStub?

    @Query(
        """
       SELECT Issue.* FROM Issue
        INNER JOIN ArticleImageJoin
        INNER JOIN SectionImageJoin
        INNER JOIN SectionArticleJoin
        INNER JOIN IssueSectionJoin
         WHERE  SectionImageJoin.imageFileName == :imageName AND SectionImageJoin.sectionFileName == IssueSectionJoin.sectionFileName
        AND IssueSectionJoin.issueDate == Issue.date AND IssueSectionJoin.issueFeedName == Issue.feedName AND IssueSectionJoin.issueStatus == Issue.status
    """
    )
    abstract fun getStubForSectionImageName(imageName: String): IssueStub?

    @Query(
        """
            SELECT Issue.* FROM Issue WHERE Issue.feedName == :feedName AND Issue.date == :date AND Issue.dateDownload IS NOT NULL
        """
    )
    abstract fun getDownloadedIssuesForDayAndFeed(
        feedName: String,
        date: String
    ): List<IssueStub>


    @Query("SELECT * FROM Issue WHERE Issue.feedName IN (:feedNames) AND strftime('%s', date) <= strftime('%s', :date) ORDER BY date DESC LIMIT :limit")
    abstract fun getIssueStubsByFeedsAndDate(
        feedNames: List<String>,
        date: String,
        limit: Int
    ): List<IssueStub>

    @Query("SELECT * FROM Issue WHERE strftime('%s', date) <= strftime('%s', :date) ORDER BY date DESC LIMIT :limit")
    abstract fun getIssueStubsByDate(
        date: String,
        limit: Int
    ): List<IssueStub>

    @Query("SELECT * FROM Issue WHERE strftime('%s', date) <= strftime('%s', :date) AND status = :status ORDER BY date DESC LIMIT :limit")
    abstract fun getIssueStubsByDateAndStatus(
        date: String,
        status: IssueStatus,
        limit: Int
    ): List<IssueStub>

    @Query("SELECT dateDownload FROM Issue WHERE date = :date AND feedName = :feedName AND status = :status")
    abstract fun getDownloadDate(
        feedName: String,
        date: String,
        status: IssueStatus
    ): Date?
}
