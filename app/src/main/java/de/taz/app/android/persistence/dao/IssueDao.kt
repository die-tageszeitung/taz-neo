package de.taz.app.android.persistence.dao

import androidx.lifecycle.LiveData
import androidx.room.Dao
import androidx.room.Query
import de.taz.app.android.api.models.IssueStatus
import de.taz.app.android.api.models.IssueStub
import kotlinx.coroutines.flow.Flow
import java.util.*

@Dao
interface IssueDao : BaseDao<IssueStub> {
    @Query("SELECT * FROM Issue WHERE feedName == :feedName AND date == :date")
    suspend fun getByFeedAndDate(
        feedName: String,
        date: String
    ): List<IssueStub>

    @Query("SELECT * FROM Issue WHERE feedName == :feedName AND date == :date AND status == :status ")
    suspend fun getByFeedDateAndStatus(
        feedName: String,
        date: String,
        status: IssueStatus
    ): IssueStub?

    @Query("SELECT * FROM Issue WHERE feedName == :feedName AND strftime('%s', date) <= strftime('%s', :date) AND status == :status ORDER BY date DESC LIMIT 1 ")
    suspend fun getLatestByFeedDateAndStatus(
        feedName: String,
        date: String,
        status: IssueStatus
    ): IssueStub?

    @Query("SELECT * FROM Issue WHERE strftime('%s', date) <= strftime('%s', :fromDate) AND feedName IN (:feedNames) AND status == :status  ORDER BY date DESC LIMIT :limit")
    suspend fun getIssuesFromDateByFeed(fromDate: String, feedNames: List<String>, status: IssueStatus, limit: Int): List<IssueStub>

    @Query("SELECT lastDisplayableName FROM Issue WHERE strftime('%s', date) = strftime('%s', :fromDate) AND feedName = :feedName AND status == :status")
    suspend fun getLastDisplayable(feedName: String, fromDate: String, status: IssueStatus): String?

    @Query("SELECT * FROM Issue WHERE strftime('%s', date) <= strftime('%s', :date) ORDER BY date DESC LIMIT 1 ")
    suspend fun getLatestByDate(date: String): IssueStub?

    @Query("SELECT * FROM Issue WHERE strftime('%s', date) <= strftime('%s', :date) AND Issue.status == 'regular' ORDER BY date DESC LIMIT 1 ")
    suspend fun getLatestRegularByDate(date: String): IssueStub?

    @Query("SELECT * FROM Issue WHERE feedName == :feedName AND date == :date AND status == :status ")
    fun getByFeedDateAndStatusLiveData(
        feedName: String,
        date: String,
        status: IssueStatus
    ): LiveData<IssueStub?>

    @Query("SELECT * FROM Issue ORDER BY date DESC LIMIT 1")
    suspend fun getLatest(): IssueStub?

    @Query("SELECT * FROM Issue WHERE Issue.status == :status AND Issue.feedName == :feedName ORDER BY date DESC LIMIT 1")
    suspend fun getLatestByFeedAndStatus(status: IssueStatus, feedName: String): IssueStub?

    @Query("SELECT * FROM Issue ORDER BY date DESC LIMIT 1")
    fun getLatestLiveData(): LiveData<IssueStub?>

    @Query("SELECT * FROM Issue ORDER BY date DESC")
    fun getAllLiveData(): LiveData<List<IssueStub>>

    @Query("SELECT * FROM Issue WHERE Issue.status != \"public\" ORDER BY date DESC")
    fun getAllLiveDataExceptPublic(): LiveData<List<IssueStub>?>

    @Query("SELECT * FROM Issue ORDER BY date DESC")
    suspend fun getAllIssueStubs(): List<IssueStub>

    @Query("SELECT * FROM Issue WHERE dateDownload IS NOT NULL ORDER BY date DESC")
    suspend fun getAllDownloadedIssueStubs(): List<IssueStub>

    @Query("SELECT * FROM Issue WHERE status IN (\"public\", \"demo\") ORDER BY date DESC")
    suspend fun getAllPublicAndDemoIssueStubs(): List<IssueStub>


    @Query("SELECT * FROM Issue WHERE feedName == :feedName AND date == :date")
    fun getByFeedAndDateLiveData(
        feedName: String,
        date: String
    ): LiveData<List<IssueStub>>

    /**
     * We exclude the last viewed issue from deletion, otherwise we might end up deleting an issue
     * that is currently being read.
     * Apart from the mentioned issue we will delete the one downloaded the furthest in the past.
     */
    @Query("""
        SELECT * FROM Issue
        WHERE NOT lastViewedDate IS (SELECT MAX(lastViewedDate) FROM Issue)
        ORDER BY dateDownload ASC LIMIT 1
        """)
    suspend fun getIssueToDelete(): IssueStub?

    @Query("SELECT * FROM Issue ORDER BY date ASC LIMIT 1")
    suspend fun getEarliest(): IssueStub?

    @Query("SELECT COUNT(date) FROM Issue WHERE dateDownload IS NOT NULL")
    fun getDownloadedIssuesCountFlow(): Flow<Int>

    @Query("SELECT EXISTS (SELECT * FROM Issue WHERE dateDownload IS NOT NULL AND feedName == :feedName AND date == :date AND status == :status)")
    fun isDownloadedLiveData(
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
    suspend fun getStubForArticleImageName(imageName: String): IssueStub?

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
    suspend fun getStubForSectionImageName(imageName: String): IssueStub?

    @Query(
        """
            SELECT Issue.* FROM Issue WHERE Issue.feedName == :feedName AND Issue.date == :date AND Issue.dateDownload IS NOT NULL
        """
    )
    suspend fun getDownloadedIssuesForDayAndFeed(
        feedName: String,
        date: String
    ): List<IssueStub>


    @Query("SELECT * FROM Issue WHERE Issue.feedName IN (:feedNames) AND strftime('%s', date) <= strftime('%s', :date) ORDER BY date DESC LIMIT :limit")
    suspend fun getIssueStubsByFeedsAndDate(
        feedNames: List<String>,
        date: String,
        limit: Int
    ): List<IssueStub>

    @Query("SELECT * FROM Issue WHERE strftime('%s', date) <= strftime('%s', :date) ORDER BY date DESC LIMIT :limit")
    suspend fun getIssueStubsByDate(
        date: String,
        limit: Int
    ): List<IssueStub>

    @Query("SELECT * FROM Issue WHERE strftime('%s', date) <= strftime('%s', :date) AND status = :status ORDER BY date DESC LIMIT :limit")
    suspend fun getIssueStubsByDateAndStatus(
        date: String,
        status: IssueStatus,
        limit: Int
    ): List<IssueStub>

    @Query("SELECT dateDownload FROM Issue WHERE date = :date AND feedName = :feedName AND status = :status")
    suspend fun getDownloadDate(
        feedName: String,
        date: String,
        status: IssueStatus
    ): Date?

    @Query("SELECT dateDownloadWithPages FROM Issue WHERE date = :date AND feedName = :feedName AND status = :status")
    suspend fun getDownloadDateWithPages(
        feedName: String,
        date: String,
        status: IssueStatus
    ): Date?
}
