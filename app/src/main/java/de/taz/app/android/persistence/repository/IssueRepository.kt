package de.taz.app.android.persistence.repository

import android.content.Context
import android.database.sqlite.SQLiteConstraintException
import androidx.annotation.UiThread
import androidx.lifecycle.LiveData
import androidx.lifecycle.Transformations
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

open class IssueRepository private constructor(applicationContext: Context) :
    RepositoryBase(applicationContext) {

    companion object : SingletonHolder<IssueRepository, Context>(::IssueRepository)

    private val articleRepository = ArticleRepository.getInstance(applicationContext)
    private val pageRepository = PageRepository.getInstance(applicationContext)
    private val sectionRepository = SectionRepository.getInstance(applicationContext)
    private val momentRepository = MomentRepository.getInstance(applicationContext)

    open fun save(issues: List<Issue>) {
        issues.forEach { save(it) }
    }

    @UiThread
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

    @UiThread
    fun exists(issueOperations: IssueOperations): Boolean {
        return getStub(
            issueOperations.feedName,
            issueOperations.date,
            issueOperations.status
        ) != null
    }

    @UiThread
    fun update(issueStub: IssueStub) {
        appDatabase.issueDao().update(issueStub)
    }

    @UiThread
    fun getStub(issueFeedName: String, issueDate: String, issueStatus: IssueStatus): IssueStub? {
        return appDatabase.issueDao().getByFeedDateAndStatus(issueFeedName, issueDate, issueStatus)
    }

    @UiThread
    fun getLatestIssueStub(): IssueStub? {
        return appDatabase.issueDao().getLatest()
    }

    @UiThread
    fun getLatestIssue(): Issue? {
        return getLatestIssueStub()?.let { issueStubToIssue(it) }
    }

    @UiThread
    fun getIssueStubByFeedAndDate(feedName: String, date: String, status: IssueStatus): IssueStub? {
        return appDatabase.issueDao().getByFeedDateAndStatus(feedName, date, status)
    }

    @UiThread
    fun getIssueStubByImprintFileName(imprintFileName: String): IssueStub? {
        return appDatabase.issueImprintJoinDao().getIssueForImprintFileName(imprintFileName)
    }

    @UiThread
    fun getIssueByFeedAndDate(feedName: String, date: String, status: IssueStatus): Issue? {
        return getIssueStubByFeedAndDate(feedName, date, status)?.let {
            issueStubToIssue(it)
        }
    }

    @UiThread
    fun getIssueStubForSection(sectionFileName: String): IssueStub {
        return appDatabase.issueSectionJoinDao().getIssueStubForSection(sectionFileName)
    }

    @UiThread
    fun getIssueForSection(sectionFileName: String): Issue {
        return issueStubToIssue(getIssueStubForSection(sectionFileName))
    }

    @UiThread
    fun getIssueStubForMoment(moment: Moment): IssueStub {
        return appDatabase.issueMomentJoinDao().getIssueStub(moment.imageList.first().name)
    }

    @UiThread
    fun getAllStubsLiveData(): LiveData<List<IssueStub>> {
        return Transformations.map(appDatabase.issueDao().getAllLiveData()) { input ->
            input ?: emptyList()
        }
    }

    @UiThread
    private fun getAllIssuesList(): List<IssueStub> {
        return appDatabase.issueDao().getAllIssueStubs()
    }

    @UiThread
    private fun getIssuesListByStatus(issueStatus: IssueStatus): List<IssueStub> {
        return appDatabase.issueDao().getIssueStubsByStatus(issueStatus)
    }

    @UiThread
    fun getAllStubsExceptPublicLiveData(): LiveData<List<IssueStub>> {
        return Transformations.map(appDatabase.issueDao().getAllLiveDataExceptPublic()) { input ->
            input ?: emptyList()
        }
    }

    @UiThread
    fun getAllDownloadedStubs(): List<IssueStub>? {
        return appDatabase.issueDao().getAllDownloaded()
    }

    @UiThread
    fun getImprintStub(
        issueFeedName: String,
        issueDate: String,
        issueStatus: IssueStatus
    ): ArticleStub? {
        val imprintName = appDatabase.issueImprintJoinDao().getImprintNameForIssue(
            issueFeedName, issueDate, issueStatus
        )
        return imprintName?.let { articleRepository.getStub(it) }
    }


    @UiThread
    fun getEarliestDownloadedIssue(): Issue? {
        return getEarliestDownloadedIssueStub()?.let { issueStubToIssue(it) }
    }

    @UiThread
    fun getEarliestDownloadedIssueStub(): IssueStub? {
        return appDatabase.issueDao().getEarliestDownloaded()
    }

    @UiThread
    fun getImprint(issueOperations: IssueOperations): Article? {
        return getImprint(issueOperations.feedName, issueOperations.date, issueOperations.status)
    }

    @UiThread
    fun getImprint(issueFeedName: String, issueDate: String, issueStatus: IssueStatus): Article? {
        val imprintName = appDatabase.issueImprintJoinDao().getImprintNameForIssue(
            issueFeedName, issueDate, issueStatus
        )
        return imprintName?.let { articleRepository.get(it) }
    }

    @UiThread
    fun setDownloadDate(issue: Issue, dateDownload: Date) {
        setDownloadDate(IssueStub(issue), dateDownload)
    }

    @UiThread
    fun setDownloadDate(issueStub: IssueStub, dateDownload: Date){
        appDatabase.runInTransaction {
            update(issueStub.copy(dateDownload = dateDownload))
        }

    }

    @UiThread
    fun resetDownloadDate(issue: Issue) {
        resetDownloadDate(IssueStub(issue))
    }

    @UiThread
    fun resetDownloadDate(issueStub: IssueStub) {
        appDatabase.runInTransaction {
            update(issueStub.copy(dateDownload = null))
        }
    }

    @UiThread
    private fun issueStubToIssue(issueStub: IssueStub): Issue {
        val sectionNames = appDatabase.issueSectionJoinDao().getSectionNamesForIssue(issueStub)
        val sections = sectionNames.map { sectionRepository.getOrThrow(it) }

        val imprint = appDatabase.issueImprintJoinDao().getImprintNameForIssue(
            issueStub.feedName, issueStub.date, issueStub.status
        )?.let { articleRepository.get(it) }

        val moment = Moment(
            appDatabase.issueMomentJoinDao().getMomentFiles(
                issueStub.feedName,
                issueStub.date,
                issueStub.status
            )
        )

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
            issueStub.zipName,
            issueStub.zipPdfName,
            issueStub.navButton,
            imprint,
            issueStub.fileList,
            issueStub.fileListPdf,
            sections,
            pageList
        )

    }

    @UiThread
    fun getIssue(issueStub: IssueStub): Issue {
        return issueStubToIssue(issueStub)
    }

    @UiThread
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

    @UiThread
    fun deletePublicIssues() {
        CoroutineScope(Dispatchers.IO).launch {
            getIssuesListByStatus(IssueStatus.public).forEach {
                delete(issueStubToIssue(it))
            }
        }
    }

}
