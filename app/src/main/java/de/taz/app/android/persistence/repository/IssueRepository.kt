package de.taz.app.android.persistence.repository

import android.content.Context
import android.database.sqlite.SQLiteConstraintException
import androidx.lifecycle.LiveData
import androidx.lifecycle.Transformations
import androidx.lifecycle.liveData
import androidx.lifecycle.switchMap
import de.taz.app.android.annotation.Mockable
import de.taz.app.android.api.ApiService
import de.taz.app.android.api.interfaces.IssueOperations
import de.taz.app.android.api.models.*
import de.taz.app.android.persistence.join.IssueImprintJoin
import de.taz.app.android.persistence.join.IssuePageJoin
import de.taz.app.android.persistence.join.IssueSectionJoin
import de.taz.app.android.util.SingletonHolder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.*

@Mockable
class IssueRepository private constructor(applicationContext: Context) :
    RepositoryBase(applicationContext) {

    companion object : SingletonHolder<IssueRepository, Context>(::IssueRepository)

    private val articleRepository = ArticleRepository.getInstance(applicationContext)
    private val pageRepository = PageRepository.getInstance(applicationContext)
    private val sectionRepository = SectionRepository.getInstance(applicationContext)
    private val momentRepository = MomentRepository.getInstance(applicationContext)

    fun save(issues: List<Issue>) {
        issues.forEach { save(it) }
    }

    fun save(issue: Issue) {
        appDatabase.runInTransaction {
            appDatabase.issueDao().insertOrReplace(
                IssueStub(issue)
            )

            // save pages
            pageRepository.save(issue.pageList)

            // save page relation
            appDatabase.issuePageJoinDao().insertOrReplace(
                issue.pageList.mapIndexed { index, page ->
                    IssuePageJoin(
                        issue.feedName,
                        issue.date,
                        issue.status,
                        page.pagePdf.name,
                        index
                    )
                }
            )

            // save moment
            momentRepository.save(issue.moment, issue.feedName, issue.date, issue.status)

            // save imprint
            issue.imprint?.let { imprint ->
                articleRepository.save(imprint)
                appDatabase.issueImprintJoinDao().insertOrReplace(
                    IssueImprintJoin(
                        issue.feedName,
                        issue.date,
                        issue.status,
                        imprint.articleHtml.name
                    )
                )
            }

            // save sections
            issue.sectionList.let { sectionList ->
                sectionList.forEach { sectionRepository.save(it) }
                appDatabase.issueSectionJoinDao()
                    .insertOrReplace(sectionList.mapIndexed { index, it ->
                        IssueSectionJoin(
                            issue.feedName,
                            issue.date,
                            issue.status,
                            it.sectionHtml.name,
                            index
                        )
                    })
            }
        }
    }

    fun exists(issueOperations: IssueOperations): Boolean {
        return getStub(
            issueOperations.feedName,
            issueOperations.date,
            issueOperations.status
        ) != null
    }

    fun saveIfDoNotExist(issues: List<Issue>) {
        issues.forEach { saveIfDoesNotExist(it) }
    }

    fun saveIfDoesNotExist(issue: Issue) {
        if (!exists(issue)) {
            save(issue)
        }
    }

    fun update(issueStub: IssueStub) {
        appDatabase.issueDao().update(issueStub)
    }

    fun getStub(issueOperations: IssueOperations) =
        getStub(issueOperations.feedName, issueOperations.date, issueOperations.status)

    fun getStub(issueFeedName: String, issueDate: String, issueStatus: IssueStatus): IssueStub? {
        return appDatabase.issueDao().getByFeedDateAndStatus(issueFeedName, issueDate, issueStatus)
    }

    fun getStubLiveData(
        issueFeedName: String,
        issueDate: String,
        issueStatus: IssueStatus
    ): LiveData<IssueStub?> {
        return appDatabase.issueDao()
            .getByFeedDateAndStatusLiveData(issueFeedName, issueDate, issueStatus)
    }

    fun getEarliestIssueStub(): IssueStub? {
        return appDatabase.issueDao().getEarliest()
    }

    fun getEarliestIssue(): Issue? {
        return getEarliestIssueStub()?.let { issueStubToIssue(it) }
    }

    fun getLatestIssueStub(): IssueStub? {
        return appDatabase.issueDao().getLatest()
    }

    fun getLatestIssue(): Issue? {
        return getLatestIssueStub()?.let { issueStubToIssue(it) }
    }

    fun getIssueStubByFeedAndDate(feedName: String, date: String, status: IssueStatus): IssueStub? {
        return appDatabase.issueDao().getByFeedDateAndStatus(feedName, date, status)
    }

    fun getLatestIssueStubByDate(date: String): IssueStub? {
        return appDatabase.issueDao().getLatestByDate(date)
    }

    fun getLatestIssueStubByFeedAndDate(
        feedName: String,
        date: String,
        status: IssueStatus
    ): IssueStub? {
        return appDatabase.issueDao().getLatestByFeedDateAndStatus(feedName, date, status)
    }

    fun getIssueStubByImprintFileName(imprintFileName: String): IssueStub? {
        return appDatabase.issueImprintJoinDao().getIssueForImprintFileName(imprintFileName)
    }

    fun getIssueByFeedAndDate(feedName: String, date: String, status: IssueStatus): Issue? {
        return getIssueStubByFeedAndDate(feedName, date, status)?.let {
            issueStubToIssue(it)
        }
    }

    fun getIssueStubForSection(sectionFileName: String): IssueStub {
        return appDatabase.issueSectionJoinDao().getIssueStubForSection(sectionFileName)
    }

    fun getIssueForSection(sectionFileName: String): Issue {
        return issueStubToIssue(getIssueStubForSection(sectionFileName))
    }

    fun getIssueStubForMoment(moment: Moment): IssueStub? {
        return moment.getMomentImage()?.let {
            appDatabase.issueImageMomentJoinDao().getIssueStub(it.name)
        }
    }

    fun getAllStubsLiveData(): LiveData<List<IssueStub>> {
        return Transformations.map(appDatabase.issueDao().getAllLiveData()) { input ->
            input ?: emptyList()
        }
    }

    private fun getAllIssuesList(): List<IssueStub> {
        return appDatabase.issueDao().getAllIssueStubs()
    }

    private fun getIssuesListByStatus(issueStatus: IssueStatus): List<IssueStub> {
        return appDatabase.issueDao().getIssueStubsByStatus(issueStatus)
    }

    fun getAllStubsExceptPublicLiveData(): LiveData<List<IssueStub>> {
        return Transformations.map(appDatabase.issueDao().getAllLiveDataExceptPublic()) { input ->
            input ?: emptyList()
        }
    }

    fun getAllDownloadedStubs(): List<IssueStub>? {
        return appDatabase.issueDao().getAllDownloaded()
    }

    fun getAllDownloadedStubsLiveData(): LiveData<List<IssueStub>?> {
        return appDatabase.issueDao().getAllDownloadedLiveData()
    }

    fun getImprintStub(issueStub: IssueOperations) = getImprintStub(
        issueStub.feedName,
        issueStub.date,
        issueStub.status
    )

    fun getImprintStub(
        issueFeedName: String,
        issueDate: String,
        issueStatus: IssueStatus
    ): ArticleStub? {
        val imprintName = appDatabase.issueImprintJoinDao().getArticleImprintNameForIssue(
            issueFeedName, issueDate, issueStatus
        )
        return imprintName?.let { articleRepository.getStub(it) }
    }


    fun getEarliestDownloadedIssue(): Issue? {
        return getEarliestDownloadedIssueStub()?.let { issueStubToIssue(it) }
    }

    fun getEarliestDownloadedIssueStub(): IssueStub? {
        return appDatabase.issueDao().getEarliestDownloaded()
    }

    fun getImprint(issueOperations: IssueOperations): Article? {
        return getImprint(issueOperations.feedName, issueOperations.date, issueOperations.status)
    }

    fun getImprint(issueFeedName: String, issueDate: String, issueStatus: IssueStatus): Article? {
        val imprintName = appDatabase.issueImprintJoinDao().getArticleImprintNameForIssue(
            issueFeedName, issueDate, issueStatus
        )
        return imprintName?.let { articleRepository.get(it) }
    }

    fun setDownloadDate(issue: Issue, dateDownload: Date) {
        setDownloadDate(IssueStub(issue), dateDownload)
    }

    fun setDownloadDate(issueStub: IssueStub, dateDownload: Date) {
        update(issueStub.copy(dateDownload = dateDownload))
    }

    fun resetDownloadDate(issue: Issue) {
        resetDownloadDate(IssueStub(issue))
    }

    fun resetDownloadDate(issueStub: IssueStub) {
        update(issueStub.copy(dateDownload = null))
    }

    private fun issueStubToIssue(issueStub: IssueStub): Issue {
        val sectionNames = appDatabase.issueSectionJoinDao().getSectionNamesForIssue(issueStub)
        val sections = sectionNames.map { sectionRepository.getOrThrow(it) }

        val imprint = appDatabase.issueImprintJoinDao().getArticleImprintNameForIssue(
            issueStub.feedName, issueStub.date, issueStub.status
        )?.let { articleRepository.get(it) }

        val moment = momentRepository.getOrThrow(issueStub)

        val pageList =
            appDatabase.issuePageJoinDao()
                .getPageNamesForIssue(issueStub.feedName, issueStub.date, issueStub.status)
                .map {
                    pageRepository.getOrThrow(it)
                }

        return Issue(
            issueStub.feedName,
            issueStub.date,
            moment,
            issueStub.key,
            issueStub.baseUrl,
            issueStub.status,
            issueStub.minResourceVersion,
            imprint,
            issueStub.isWeekend,
            sections,
            pageList,
            issueStub.dateDownload,
            issueStub.downloadedStatus
        )

    }

    fun getIssue(issueOperations: IssueOperations) = getIssue(
        issueOperations.feedName,
        issueOperations.date,
        issueOperations.status
    )

    fun getIssueLiveData(issueOperations: IssueOperations) = getIssueLiveData(
        issueOperations.feedName,
        issueOperations.date,
        issueOperations.status
    )

    fun getIssue(issueFeedName: String, issueDate: String, issueStatus: IssueStatus): Issue? {
        return getStub(issueFeedName, issueDate, issueStatus)?.let { getIssue(it) }
    }

    fun getIssueStubForImage(image: Image): IssueStub? {
        return appDatabase.issueDao().getStubForArticleImageName(image.name)
            ?: appDatabase.issueDao().getStubForSectionImageName(image.name)
    }

    fun getIssueLiveData(
        issueFeedName: String,
        issueDate: String,
        issueStatus: IssueStatus
    ): LiveData<Issue?> =
        getStubLiveData(issueFeedName, issueDate, issueStatus).switchMap { issueStub ->
            liveData(Dispatchers.IO) {
                issueStub?.let {
                    emit(getIssue(issueStub))
                } ?: emit(null)
            }
        }

    fun getIssue(issueStub: IssueStub): Issue {
        return issueStubToIssue(issueStub)
    }

    fun delete(issueFeedName: String, issueDate: String, issueStatus: IssueStatus) {
        getIssue(issueFeedName, issueDate, issueStatus)?.let { delete(it) }
    }

    fun delete(issue: Issue) {
        appDatabase.runInTransaction {
            // delete moment
            momentRepository.delete(issue.moment, issue.feedName, issue.date, issue.status)

            // delete imprint
            issue.imprint?.let { imprint ->
                appDatabase.issueImprintJoinDao().delete(
                    IssueImprintJoin(
                        issue.feedName,
                        issue.date,
                        issue.status,
                        imprint.articleHtml.name
                    )
                )
                try {
                    articleRepository.delete(imprint)
                } catch (e: SQLiteConstraintException) {
                    // do not delete used by another issue
                }
            } ?: appDatabase.issueImprintJoinDao().getLeftOverImprintNameForIssue(
                issue.feedName,
                issue.date,
                issue.status
            )?.let {
                log.warn("There is a left over imprint with no entry in article table. Will be deleted: $it")
                appDatabase.issueImprintJoinDao().delete(
                    IssueImprintJoin(
                        issue.feedName,
                        issue.date,
                        issue.status,
                        it
                    )
                )
            }

            // delete page relation
            appDatabase.issuePageJoinDao().delete(
                issue.pageList.mapIndexed { index, page ->
                    IssuePageJoin(
                        issue.feedName,
                        issue.date,
                        issue.status,
                        page.pagePdf.name,
                        index
                    )
                }
            )
            // delete pages
            pageRepository.delete(issue.pageList)

            // delete sections
            issue.sectionList.let { sectionList ->
                appDatabase.issueSectionJoinDao()
                    .delete(sectionList.mapIndexed { index, it ->
                        IssueSectionJoin(
                            issue.feedName,
                            issue.date,
                            issue.status,
                            it.sectionHtml.name,
                            index
                        )
                    })
                sectionList.forEach { sectionRepository.delete(it) }
            }

            appDatabase.issueDao().delete(
                IssueStub(issue)
            )
        }
    }

    fun deletePublicIssues() = CoroutineScope(Dispatchers.IO).launch {
        getIssuesListByStatus(IssueStatus.public).forEach {
            delete(issueStubToIssue(it))
        }
    }

    fun deleteNotDownloadedRegularIssues() = CoroutineScope(Dispatchers.IO).launch {
        getIssuesListByStatus(IssueStatus.regular).forEach { issueStub ->
            if (issueStub.downloadedStatus != DownloadStatus.done) {
                CoroutineScope(Dispatchers.IO).launch {
                    ApiService.getInstance().getIssueByFeedAndDateAsync(
                        issueStub.feedName, issueStub.date
                    ).await()?.let {
                        save(it)
                    }

                    launch {
                        getIssue(issueStub).delete()
                    }
                }
            }
        }
    }

    fun getDownloadStartedIssues(): List<Issue> {
        return appDatabase.issueDao().getDownloadStartedIssues().map { issueStubToIssue(it) }
    }

    fun isDownloadedLiveData(issueOperations: IssueOperations) = isDownloadedLiveData(
        issueOperations.feedName,
        issueOperations.date,
        issueOperations.status
    )

    fun isDownloadedLiveData(
        feedName: String,
        date: String,
        status: IssueStatus
    ): LiveData<Boolean> {
        return appDatabase.issueDao().isDownloadedLiveData(feedName, date, status)
    }

}
