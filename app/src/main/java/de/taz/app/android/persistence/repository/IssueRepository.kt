package de.taz.app.android.persistence.repository

import android.content.Context
import androidx.lifecycle.LiveData
import androidx.lifecycle.Transformations
import de.taz.app.android.api.interfaces.IssueOperations
import de.taz.app.android.api.models.*
import de.taz.app.android.download.DownloadService
import de.taz.app.android.persistence.join.IssueImprintJoin
import de.taz.app.android.persistence.join.IssuePageJoin
import de.taz.app.android.persistence.join.IssueSectionJoin
import de.taz.app.android.util.Log
import de.taz.app.android.util.SingletonHolder

open class IssueRepository private constructor(applicationContext: Context) :
    RepositoryBase(applicationContext) {

    companion object : SingletonHolder<IssueRepository, Context>(::IssueRepository)

    private val log by Log

    private val articleRepository = ArticleRepository.getInstance(applicationContext)
    private val pageRepository = PageRepository.getInstance(applicationContext)
    private val sectionRepository = SectionRepository.getInstance(applicationContext)
    private val momentRepository = MomentRepository.getInstance(applicationContext)

    open fun save(issues: List<Issue>) {
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
                    IssuePageJoin(issue.feedName, issue.date, page.pagePdf.name, index)
                }
            )

            // save moment
            momentRepository.save(issue.moment, issue.feedName, issue.date)

            // save imprint
            issue.imprint?.let { imprint ->
                articleRepository.save(imprint)
                appDatabase.issueImprintJoinDao().insertOrReplace(
                    IssueImprintJoin(issue.feedName, issue.date, imprint.articleHtml.name)
                )
            }

            // save sections
            issue.sectionList.let { sectionList ->
                sectionList.forEach { sectionRepository.save(it) }
                appDatabase.issueSectionJoinDao()
                    .insertOrReplace(sectionList.mapIndexed { index, it ->
                        IssueSectionJoin(issue.feedName, issue.date, it.sectionHtml.name, index)
                    })
            }
        }
    }

    fun exists(issueOperations: IssueOperations): Boolean {
        return getStub(issueOperations.feedName, issueOperations.date) != null
    }

    fun getStub(issueFeedName: String, issueDate: String): IssueStub? {
        return appDatabase.issueDao().getByFeedAndDate(issueFeedName, issueDate)
    }

    fun getLatestIssueStub(): IssueStub? {
        return appDatabase.issueDao().getLatest()
    }

    fun getLatestIssue(): Issue? {
        return getLatestIssueStub()?.let { issueStubToIssue(it) }
    }

    fun getLatestIssueStubLiveData(): LiveData<IssueStub?> {
        return appDatabase.issueDao().getLatestLiveData()
    }

    @Throws(NotFoundException::class)
    fun getLatestIssueStubOrThrow(): IssueStub {
        return appDatabase.issueDao().getLatest() ?: throw NotFoundException()
    }

    @Throws(NotFoundException::class)
    fun getLatestIssueOrThrow(): Issue {
        return getLatestIssueStub()?.let { issueStubToIssue(it) } ?: throw NotFoundException()
    }

    fun getIssueStubByFeedAndDate(feedName: String, date: String): IssueStub? {
        return appDatabase.issueDao().getByFeedAndDate(feedName, date)
    }

    fun getIssueStubByImprintFileName(imprintFileName: String): IssueStub? {
        return appDatabase.issueImprintJoinDao().getIssueForImprintFileName(imprintFileName)
    }

    fun getIssueByFeedAndDate(feedName: String, date: String): Issue? {
        return getIssueStubByFeedAndDate(feedName, date)?.let {
            issueStubToIssue(it)
        }
    }

    fun getIssueStubForSection(sectionFileName: String): IssueStub {
        return appDatabase.issueSectionJoinDao().getIssueStubForSection(sectionFileName)
    }

    fun getIssueForSection(sectionFileName: String): Issue {
        return issueStubToIssue(getIssueStubForSection(sectionFileName))
    }

    fun getIssueStubForMoment(moment: Moment): IssueStub {
        return appDatabase.issueMomentJoinDao().getIssueStub(moment.imageList.first().name)
    }

    fun getAllStubsLiveData(): LiveData<List<IssueStub>> {
        return Transformations.map(appDatabase.issueDao().getAllLiveData()) { input ->
            input ?: emptyList()
        }
    }

    private fun getAllIssuesList(): List<IssueStub> {
        return appDatabase.issueDao().getAllIssueStubs()
    }

    private fun issueStubToIssue(issueStub: IssueStub): Issue {
        val sectionNames = appDatabase.issueSectionJoinDao().getSectionNamesForIssue(issueStub)
        val sections = sectionNames.map { sectionRepository.getOrThrow(it) }

        val imprint = appDatabase.issueImprintJoinDao().getImprintNameForIssue(
            issueStub.feedName, issueStub.date
        )?.let { articleRepository.get(it) }

        val moment = Moment(
            appDatabase.issueMomentJoinDao().getMomentFiles(
                issueStub.feedName,
                issueStub.date
            )
        )

        val pageList =
            appDatabase.issuePageJoinDao().getPageNamesForIssue(issueStub.feedName, issueStub.date)
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

    fun getIssue(issueStub: IssueStub): Issue {
        return issueStubToIssue(issueStub)
    }

    fun delete(issue: Issue) {
        // stop all current downloads
        DownloadService.cancelAllDownloads()

        // delete moment
        momentRepository.delete(issue.moment, issue.feedName, issue.date)

        // delete imprint
        issue.imprint?.let { imprint ->
            appDatabase.issueImprintJoinDao().delete(
                IssueImprintJoin(issue.feedName, issue.date, imprint.articleHtml.name)
            )
            articleRepository.delete(imprint)
        }
        // delete page relation
        appDatabase.issuePageJoinDao().delete(
            issue.pageList.mapIndexed { index, page ->
                IssuePageJoin(issue.feedName, issue.date, page.pagePdf.name, index)
            }
        )
        // delete pages
        pageRepository.delete(issue.pageList)

        // delete sections
        issue.sectionList.let { sectionList ->
            appDatabase.issueSectionJoinDao()
                .delete(sectionList.mapIndexed { index, it ->
                    IssueSectionJoin(issue.feedName, issue.date, it.sectionHtml.name, index)
                })
            try {
                sectionList.forEach { sectionRepository.delete(it) }
            } catch (e: Exception) {
                log.warn(e.toString())
            }
        }

        try {
            appDatabase.issueDao().delete(
                IssueStub(issue)
            )
        } catch (e: Exception) {
            log.warn(e.toString())
        }
        // TODO actually delete files! perhaps decide if to keep some

    }

    fun deleteAllIssues() {
        getAllIssuesList().forEach {
            delete(issueStubToIssue(it))
        }
    }

}