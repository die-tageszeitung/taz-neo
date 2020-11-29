package de.taz.app.android.persistence.repository

import android.content.Context
import android.database.sqlite.SQLiteConstraintException
import android.os.Parcelable
import androidx.lifecycle.LiveData
import androidx.lifecycle.Transformations
import de.taz.app.android.annotation.Mockable
import de.taz.app.android.api.interfaces.IssueOperations
import de.taz.app.android.api.interfaces.ObservableDownload
import de.taz.app.android.api.models.*
import de.taz.app.android.persistence.join.IssueImprintJoin
import de.taz.app.android.persistence.join.IssuePageJoin
import de.taz.app.android.persistence.join.IssueSectionJoin
import de.taz.app.android.simpleDateFormat
import de.taz.app.android.util.SingletonHolder
import io.sentry.core.Sentry
import kotlinx.android.parcel.Parcelize
import java.util.*

@Mockable
class IssueRepository private constructor(val applicationContext: Context) :
    RepositoryBase(applicationContext) {

    companion object : SingletonHolder<IssueRepository, Context>(::IssueRepository)

    private val articleRepository = ArticleRepository.getInstance(applicationContext)
    private val pageRepository = PageRepository.getInstance(applicationContext)
    private val sectionRepository = SectionRepository.getInstance(applicationContext)
    private val momentRepository = MomentRepository.getInstance(applicationContext)
    private val viewerStateRepository = ViewerStateRepository.getInstance(applicationContext)


    fun save(issues: List<Issue>) {
        issues.forEach { save(it) }
    }

    fun save(issue: Issue): Issue {
        log.info("saving issue: ${issue.tag}")
        appDatabase.runInTransaction<Void> {
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
            momentRepository.save(issue.moment)

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
            null
        }
        return issue
    }

    fun exists(issueOperations: IssueOperations): Boolean {
        return getStub(
            IssueKey(
                issueOperations.feedName,
                issueOperations.date,
                issueOperations.status
            )
        )?.let { true } ?: false
    }

    fun exists(issueKey: IssueKey): Boolean {
        return getStub(issueKey)?.let { true } ?: false
    }

    fun saveIfNotExistOrOutdated(issue: Issue): Issue {
        val existing = get(issue.issueKey)
        return existing?.let {
            when {
                existing.moTime < issue.moTime -> {
                    save(issue)
                    issue
                }
                else -> {
                    existing
                }
            }
        } ?: saveIfDoesNotExist(issue)
    }

    fun saveIfNotExistOrOutdated(issues: List<Issue>): List<Issue> {
        return issues.map { saveIfNotExistOrOutdated(it) }
    }

    fun saveIfDoesNotExist(issues: List<Issue>): List<Issue> {
        return issues.map { saveIfDoesNotExist(it) }
    }

    fun saveIfDoesNotExist(issue: Issue): Issue {
        if (!exists(issue)) {
            save(issue)
        }
        return issue
    }

    fun update(issueStub: IssueStub) {
        appDatabase.issueDao().update(issueStub)
    }

    fun getLastDisplayable(issueKey: IssueKey): String? {
        return appDatabase.issueDao().getLastDisplayable(issueKey.feedName, issueKey.date, issueKey.status)
    }

    fun saveLastDisplayable(issueKey: IssueKey, displayableName: String) {
        appDatabase.runInTransaction {
            viewerStateRepository.saveIfNotExists(displayableName, 0)
            val stub = getStub(issueKey)
            stub?.copy(lastDisplayableName = displayableName)?.let {
                update(it)
            }
        }
    }

    fun get(issueKey: IssueKey): Issue? {
        return getStub(issueKey)?.let { issueStubToIssue(it) }
    }

    fun getStub(issueKey: IssueKey): IssueStub? {
        return appDatabase.issueDao()
            .getByFeedDateAndStatus(issueKey.feedName, issueKey.date, issueKey.status)
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

    fun getLatestIssueStub(feedNames: List<String>, limit: Int): IssueStub? {
        return appDatabase.issueDao().getLatest()
    }

    fun getIssuesFromDate(
        fromDate: Date,
        feedNames: List<String>,
        status: IssueStatus,
        limit: Int = 10
    ): List<IssueStub> {
        return appDatabase.issueDao().getIssuesFromDateByFeed(
            simpleDateFormat.format(fromDate),
            feedNames,
            status,
            limit
        )
    }

    fun getLatestIssueByFeedAndStatus(feedName: String, status: IssueStatus): IssueStub? {
        return appDatabase.issueDao().getLatestByFeedAndStatus(status, feedName)
    }

    fun getIssueStubByFeedAndDate(feedName: String, date: String, status: IssueStatus): IssueStub? {
        return appDatabase.issueDao().getByFeedDateAndStatus(feedName, date, status)
    }

    fun getLatestIssueStubByDate(date: String): IssueStub? {
        return appDatabase.issueDao().getLatestByDate(date)
    }

    fun getLatestRegularIssueStubByDate(date: String): IssueStub? {
        return appDatabase.issueDao().getLatestRegularByDate(date)
    }

    fun getLatestIssueStubsByFeedAndDate(
        date: String,
        feedNames: List<String>,
        limit: Int
    ): List<IssueStub> {
        return appDatabase.issueDao().getIssueStubsByFeedsAndDate(feedNames, date, limit)
    }

    fun getLatestIssueStubsByDate(
        date: String,
        limit: Int
    ): List<IssueStub> {
        return appDatabase.issueDao().getIssueStubsByDate(date, limit)
    }

    fun getLatestIssueStubsByDateAndStatus(
        date: String,
        status: IssueStatus,
        limit: Int
    ): List<IssueStub> {
        return appDatabase.issueDao().getIssueStubsByDateAndStatus(date, status, limit)
    }

    fun getIssueStubByImprintFileName(imprintFileName: String): IssueStub? {
        return appDatabase.issueImprintJoinDao().getIssueForImprintFileName(imprintFileName)
    }

    fun getIssueByFeedAndDate(feedName: String, date: String, status: IssueStatus): Issue? {
        return getIssueStubByFeedAndDate(feedName, date, status)?.let {
            issueStubToIssue(it)
        }
    }

    fun getIssueStubForSection(sectionFileName: String): IssueStub? {
        return appDatabase.issueSectionJoinDao().getIssueStubForSection(sectionFileName)
    }

    fun getIssueStubForArticle(articleFileName: String): IssueStub? {
        return appDatabase.issueSectionJoinDao().getIssueStubForArticle(articleFileName)
    }

    fun getIssueStubForMoment(moment: Moment): IssueStub? {
        return moment.getMomentImage()?.let {
            appDatabase.momentImageJoinJoinDao().getIssueStub(it.name)
        }
    }

    fun getAllStubsLiveData(): LiveData<List<IssueStub>> {
        return Transformations.map(appDatabase.issueDao().getAllLiveData()) { input ->
            input ?: emptyList()
        }
    }

    fun getIssueStubByIssueKey(issueKey: IssueKey): IssueStub? {
        return getIssueStubByFeedAndDate(issueKey.feedName, issueKey.date, issueKey.status)
    }

    private fun getAllIssuesList(): List<IssueStub> {
        return appDatabase.issueDao().getAllIssueStubs()
    }

    fun getIssuesListByStatus(issueStatus: IssueStatus): List<IssueStub> {
        return appDatabase.issueDao().getIssueStubsByStatus(issueStatus)
    }

    fun getAllDownloadedStubs(): List<IssueStub> {
        return appDatabase.issueDao().getAllDownloaded()
    }

    fun getAllDownloadedStubsLiveData(): LiveData<List<IssueStub>?> {
        return appDatabase.issueDao().getAllDownloadedLiveData()
    }

    fun getDownloadedIssuesCountLiveData(): LiveData<Int> {
        return appDatabase.issueDao().getDownloadedIssuesCountLiveData()
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

    private fun getEarliestDownloadedIssueStub(): IssueStub? {
        return appDatabase.issueDao().getEarliestDownloaded()
    }

    fun getImprint(issueKey: IssueKey): Article? {
        return getImprint(issueKey.feedName, issueKey.date, issueKey.status)
    }

    fun getImprint(issueFeedName: String, issueDate: String, issueStatus: IssueStatus): Article? {
        val imprintName = appDatabase.issueImprintJoinDao().getArticleImprintNameForIssue(
            issueFeedName, issueDate, issueStatus
        )
        return imprintName?.let { articleRepository.get(it) }
    }

    fun getDownloadDate(issue: IssueOperations): Date? {
        return when (issue) {
            is IssueStub -> getDownloadDate(issue)
            is Issue -> getDownloadDate(issue)
            else -> throw Exception("IssueOperations are either Issue or IssueStub")
        }
    }

    fun getDownloadDate(issue: Issue): Date? {
        return getDownloadDate(IssueStub(issue))
    }

    fun isDownloaded(issueKey: IssueKey): Boolean {
        return getDownloadDate(issueKey) != null
    }

    fun getDownloadDate(issueKey: IssueKey): Date? {
        return appDatabase.issueDao()
            .getDownloadDate(issueKey.feedName, issueKey.date, issueKey.status)
    }

    fun getDownloadDate(issueStub: IssueStub): Date? {
        return getDownloadDate(issueStub.issueKey)
    }

    fun setDownloadDate(issue: IssueOperations, dateDownload: Date?) {
        when (issue) {
            is IssueStub -> setDownloadDate(issue, dateDownload)
            is Issue -> setDownloadDate(issue, dateDownload)
        }
    }

    fun setDownloadDate(issueStub: IssueStub, dateDownload: Date?) {
        update(issueStub.copy(dateDownload = dateDownload))
    }

    fun setDownloadDate(issue: Issue, dateDownload: Date?) {
        setDownloadDate(IssueStub(issue), dateDownload)
    }

    fun resetDownloadDate(issue: Issue) {
        resetDownloadDate(IssueStub(issue))
    }

    fun resetDownloadDate(issueStub: IssueStub) {
        getStub(issueStub.issueKey)?.let {
            update(it.copy(dateDownload = null))
        }
    }

    private fun issueStubToIssue(issueStub: IssueStub): Issue {
        val sectionNames = appDatabase.issueSectionJoinDao().getSectionNamesForIssue(issueStub)
        val sections =
            sectionNames
                .map { sectionRepository.get(it) }
                .filterIndexed { index, section ->
                    // TODO: We observed consistency errors in sentry but werent able to pin down the issue. Capture and ignore any expected section
                    val isNull = section == null
                    if (isNull) {
                        Sentry.captureMessage("Expected section ${sectionNames[index]} not found in Database")
                    }
                    !isNull
                }.filterNotNull()


        val imprint = appDatabase.issueImprintJoinDao().getArticleImprintNameForIssue(
            issueStub.feedName, issueStub.date, issueStub.status
        )?.let { articleRepository.get(it) }

        val moment = momentRepository.get(issueStub)

        val pageList =
            appDatabase.issuePageJoinDao()
                .getPageNamesForIssue(issueStub.feedName, issueStub.date, issueStub.status)
                .map {
                    pageRepository.getOrThrow(it)
                }

        return Issue(
            issueStub.feedName,
            issueStub.date,
            moment!!,
            issueStub.key,
            issueStub.baseUrl,
            issueStub.status,
            issueStub.minResourceVersion,
            imprint,
            issueStub.isWeekend,
            sections,
            pageList,
            issueStub.moTime,
            issueStub.dateDownload,
            issueStub.lastDisplayableName
        )

    }

    fun getIssueStubForImage(image: Image): IssueStub {
        return appDatabase.issueDao().getStubForArticleImageName(image.name)
            ?: appDatabase.issueDao().getStubForSectionImageName(image.name)
            ?: throw NotFoundException()
    }

    fun getIssue(issueStub: IssueStub): Issue {
        return issueStubToIssue(issueStub)
    }

    fun delete(issueKey: IssueKey) {
        get(issueKey)?.let { delete(it) }
    }

    fun replace(issue: Issue) {
        appDatabase.runInTransaction<Unit> {
            delete(issue)
            save(issue)
        }
    }

    fun delete(issue: Issue) {
        log.info("deleting issue ${issue.tag}")
        // delete moment
        momentRepository.deleteMoment(issue.feedName, issue.date, issue.status)

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
                articleRepository.deleteArticle(imprint)
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


/**
 * The representation of a [feedName], [date] and [status] determining the exact
 * identity of an issue
 */
@Parcelize
data class IssueKey(
    val feedName: String,
    val date: String,
    val status: IssueStatus
) : Parcelable, ObservableDownload {
    override fun getDownloadTag(): String {
        return "$feedName/$date/$status"
    }
}

/**
 * An [IssuePublication] is the description of an Issue released at a certain [date] in a [feed],
 * omitting the specification of an [IssueStatus]
 */
@Parcelize
data class IssuePublication(
    val feed: String,
    val date: String
) : Parcelable