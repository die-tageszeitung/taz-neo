package de.taz.app.android.persistence.repository

import android.content.Context
import android.database.sqlite.SQLiteConstraintException
import android.os.Parcelable
import androidx.room.withTransaction
import de.taz.app.android.api.interfaces.IssueOperations
import de.taz.app.android.api.interfaces.ObservableDownload
import de.taz.app.android.api.models.*
import de.taz.app.android.persistence.join.IssueImprintJoin
import de.taz.app.android.persistence.join.IssuePageJoin
import de.taz.app.android.persistence.join.IssueSectionJoin
import de.taz.app.android.util.SingletonHolder
import io.sentry.Sentry
import kotlinx.coroutines.flow.Flow
import kotlinx.parcelize.Parcelize
import java.util.*


class IssueRepository private constructor(applicationContext: Context) :
    RepositoryBase(applicationContext) {

    companion object : SingletonHolder<IssueRepository, Context>(::IssueRepository)

    private val articleRepository = ArticleRepository.getInstance(applicationContext)
    private val pageRepository = PageRepository.getInstance(applicationContext)
    private val sectionRepository = SectionRepository.getInstance(applicationContext)
    private val momentRepository = MomentRepository.getInstance(applicationContext)
    private val viewerStateRepository = ViewerStateRepository.getInstance(applicationContext)

    /**
     * Save the full downloaded [Issue] metadata to the database
     * and replace any existing [Issue] with the same key.
     *
     * This will recursively save all the related models.
     * As there are many-to-many relations, replacing an existing [Issue] might result in some
     * orphaned children that have to be cleanup up by some scrubber process.
     */
    suspend fun save(issue: Issue): Issue {
        log.info("saving issue: ${issue.tag}")
        appDatabase.withTransaction {
            appDatabase.issueDao().insertOrReplace(
                IssueStub(issue)
            )

            // save pages
            issue.pageList.forEach {
                pageRepository.saveInternal(it)
            }

            // save page relation
            appDatabase.issuePageJoinDao().apply {
                deleteRelationToIssue(issue.feedName, issue.date, issue.status)
                insertOrReplace(issue.pageList.mapIndexed { index, page ->
                    IssuePageJoin(
                        issue.feedName, issue.date, issue.status, page.pagePdf.name, index
                    )
                })
            }

            // save moment
            momentRepository.saveInternal(issue.moment)

            // save imprint
            issue.imprint?.let { imprint ->
                articleRepository.saveInternal(imprint)
                appDatabase.issueImprintJoinDao().apply {
                    deleteRelationToIssue(issue.feedName, issue.date, issue.status)
                    insertOrReplace(
                        IssueImprintJoin(
                            issue.feedName, issue.date, issue.status, imprint.articleHtml.name
                        )
                    )
                }
            }

            // save sections
            appDatabase.issueSectionJoinDao().apply {
                deleteRelationToIssue(issue.feedName, issue.date, issue.status)
                issue.sectionList.let { sectionList ->
                    sectionList.forEach { sectionRepository.saveInternal(it) }
                    insertOrReplace(sectionList.mapIndexed { index, it ->
                        IssueSectionJoin(
                            issue.feedName, issue.date, issue.status, it.sectionHtml.name, index
                        )
                    })
                }
            }
        }

        // It is important to refresh the issue after this operation, as in the sub operation
        // (saving articles, sections etc.) might be business logic slightly altering the actually
        // saved data, naming bookmarked state that is being preserved, for instance.
        return requireNotNull(get(issue.issueKey)) { "Could not get Issue(${issue.issueKey}) after it was saved" }
    }

    /**
     * Change the existing issueStub in the Database
     * NOTE: Make sure to use update only after fetching the data from db and then doing the desired
     * changes - as otherwise changes might be overwritten
     */
    private suspend fun update(issueStub: IssueStub) {
        appDatabase.issueDao().update(issueStub)
    }

    suspend fun getLastDisplayable(issueKey: IssueKey): String? {
        return appDatabase.issueDao()
            .getLastDisplayable(issueKey.feedName, issueKey.date, issueKey.status)
    }

    suspend fun saveLastDisplayable(issueKey: IssueKey, displayableName: String) {
        appDatabase.withTransaction {
            viewerStateRepository.saveIfNotExists(displayableName, 0)
            getStub(issueKey)?.copy(lastDisplayableName = displayableName)?.let {
                update(it)
            }
        }
    }

    suspend fun get(issueKey: IssueKey): Issue? {
        return getStub(issueKey)?.let { issueStubToIssue(it) }
    }


    suspend fun get(issueKey: IssueKeyWithPages): IssueWithPages? {
        val issue = get(IssueKey(issueKey))
        return issue?.let { IssueWithPages(it) }
    }

    suspend fun getStub(issueKey: AbstractIssueKey): IssueStub? {
        return appDatabase.issueDao()
            .getByFeedDateAndStatus(issueKey.feedName, issueKey.date, issueKey.status)
    }

    fun getStubFlow(
        issueFeedName: String,
        issueDate: String,
        issueStatus: IssueStatus
    ): Flow<IssueStub?> {
        return appDatabase.issueDao()
            .getByFeedDateAndStatusFlow(issueFeedName, issueDate, issueStatus)
    }

    suspend fun getLatestIssueStub(): IssueStub? {
        return appDatabase.issueDao().getLatest()
    }

    suspend fun getIssueStubsByFeedAndDate(feedName: String, date: String): List<IssueStub> {
        return appDatabase.issueDao().getByFeedAndDate(feedName, date)
    }

    suspend fun getIssueStubByFeedDateAndStatus(
        feedName: String,
        date: String,
        status: IssueStatus
    ): IssueStub? {
        return appDatabase.issueDao().getByFeedDateAndStatus(feedName, date, status)
    }


    suspend fun getIssueByFeedDateAndStatus(
        feedName: String,
        date: String,
        status: IssueStatus
    ): Issue? {
        return getIssueStubByFeedDateAndStatus(feedName, date, status)?.let {
            issueStubToIssue(it)
        }
    }

    suspend fun getIssueStubByImprintFileName(imprintFileName: String): IssueStub? {
        return appDatabase.issueImprintJoinDao().getIssueForImprintFileName(imprintFileName)
    }

    suspend fun getIssuesByFeedAndDate(feedName: String, date: String): List<Issue> {
        return getIssueStubsByFeedAndDate(feedName, date).map {
            issueStubToIssue(it)
        }
    }

    /**
     * Alert - the same section can be referenced by multiple issues.
     * By convention we'll return the "most valuable" issue here
     * TODO: Clean up the DB model
     */
    suspend fun getIssueStubForSection(sectionFileName: String): IssueStub? {
        return appDatabase.issueSectionJoinDao().getIssueStubsForSection(sectionFileName)
            .maxByOrNull { it.status }
    }

    /**
     * Alert - the same section can be referenced by multiple issues.
     * By convention we'll return the "most valuable" issue here
     * TODO: Clean up the DB model
     */
    suspend fun getIssueStubForArticle(articleFileName: String): IssueStub? {
        return appDatabase.issueSectionJoinDao().getIssueStubsForArticle(articleFileName)
            .maxByOrNull { it.status }
    }

    suspend fun getIssueStubToDelete(): IssueStub? {
        return appDatabase.issueDao().getIssueToDelete()
    }

    suspend fun getImprint(issueKey: IssueKey): Article? {
        return getImprint(issueKey.feedName, issueKey.date, issueKey.status)
    }

    suspend fun getImprint(
        issueFeedName: String,
        issueDate: String,
        issueStatus: IssueStatus
    ): Article? {
        val imprintName = appDatabase.issueImprintJoinDao().getArticleImprintNameForIssue(
            issueFeedName, issueDate, issueStatus
        )
        return imprintName?.let { articleRepository.get(it) }
    }

    suspend fun getDownloadDate(issue: IssueOperations): Date? {
        return when (issue) {
            is IssueStub -> getDownloadDate(issue)
            is Issue -> getDownloadDate(issue)
            is IssueWithPages -> getDownloadDate(issue)
            else -> throw Exception("IssueOperations are either Issue or IssueStub")
        }
    }

    private suspend fun getDownloadDateWithPages(issueKey: IssueKeyWithPages): Date? {
        return appDatabase.issueDao()
            .getDownloadDateWithPages(issueKey.feedName, issueKey.date, issueKey.status)
    }

    private suspend fun getDownloadDate(issue: Issue): Date? {
        return getDownloadDate(IssueStub(issue))
    }

    private suspend fun getDownloadDate(issueWithPages: IssueWithPages): Date? {
        return getDownloadDateWithPages(issueWithPages.issueKey)
    }

    private suspend fun getDownloadDate(issueKey: IssueKey): Date? {
        return appDatabase.issueDao()
            .getDownloadDate(issueKey.feedName, issueKey.date, issueKey.status)
    }

    private suspend fun getDownloadDate(issueStub: IssueStub): Date? {
        return getDownloadDate(issueStub.issueKey)
    }

    suspend fun isDownloaded(issueKey: AbstractIssueKey): Boolean {
        return when (issueKey) {
            is IssueKey -> isDownloaded(issueKey)
            is IssueKeyWithPages -> isDownloaded(issueKey)
            else -> throw IllegalStateException("issueKey argument needs to be one of IssueKeyWithPages or IssueKey")
        }
    }

    private suspend fun isDownloaded(issueKey: IssueKey): Boolean {
        return getDownloadDate(issueKey) != null
    }

    private suspend fun isDownloaded(issueKeyWithPages: IssueKeyWithPages): Boolean {
        return getDownloadDateWithPages(issueKeyWithPages) != null
    }

    suspend fun updateLastViewedDate(issueOperations: IssueOperations) {
        updateLastViewedDate(issueOperations.issueKey)
    }

    suspend fun updateLastViewedDate(issueKey: AbstractIssueKey) {
        getStub(issueKey)?.let {
            update(it.copy(lastViewedDate = Date()))
        }
    }

    suspend fun setDownloadDate(issue: IssueOperations, dateDownload: Date?) {
        when (issue) {
            is IssueStub -> setDownloadDate(issue, dateDownload)
            is Issue -> setDownloadDate(issue, dateDownload)
            is IssueWithPages -> setDownloadDate(issue, dateDownload)
        }
    }

    private suspend fun setDownloadDate(issueStub: IssueStub, dateDownload: Date?) {
        getStub(issueStub.issueKey)?.let {
            update(it.copy(dateDownload = dateDownload))
        }
    }

    private suspend fun setDownloadDate(issueWithPages: IssueWithPages, dateDownload: Date?) {
        getStub(issueWithPages.issueKey)?.let {
            update(
                it.copy(
                    dateDownload = dateDownload,
                    dateDownloadWithPages = dateDownload
                )
            )
        }
    }

    private suspend fun setDownloadDate(issue: Issue, dateDownload: Date?) {
        setDownloadDate(IssueStub(issue), dateDownload)
    }

    private suspend fun issueStubToIssue(issueStub: IssueStub): Issue {
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
        )
        val imprintFile = try {
            imprint?.let { articleRepository.get(it) }
        } catch (nfe: NotFoundException) {
            val hint = "No imprint file for ${issueStub.issueKey} was found, this is unexpected"
            log.error(hint)
            Sentry.captureMessage(hint)
            null
        }

        val moment = momentRepository.get(issueStub) ?: run {
            val hint = "No moment for ${issueStub.issueKey} was found, this is unexpected"
            log.error(hint)
            Sentry.captureMessage(hint)
            // use dummy moment
            Moment(
                issueStub.feedName,
                issueStub.date,
                issueStub.status,
                issueStub.baseUrl,
                emptyList(),
                emptyList(),
                emptyList(),
                null
            )
        }
        val pageList =
            appDatabase.issuePageJoinDao()
                .getPageNamesForIssue(issueStub.feedName, issueStub.date, issueStub.status)
                .map {
                    pageRepository.getOrThrow(it)
                }

        return Issue(
            issueStub.feedName,
            issueStub.date,
            issueStub.validityDate,
            moment,
            issueStub.key,
            issueStub.baseUrl,
            issueStub.status,
            issueStub.minResourceVersion,
            imprintFile,
            issueStub.isWeekend,
            sections,
            pageList,
            issueStub.moTime,
            issueStub.dateDownload,
            issueStub.dateDownloadWithPages,
            issueStub.lastDisplayableName,
            issueStub.lastPagePosition,
            issueStub.lastViewedDate,
        )
    }

    suspend fun saveLastPagePosition(issueKey: IssueKey, lastPagePosition: Int) {
        getStub(issueKey)?.copy(lastPagePosition = lastPagePosition)?.let { update(it) }
    }

    suspend fun getIssueStubForImage(image: Image): IssueStub {
        return appDatabase.issueDao().getStubForArticleImageName(image.name)
            ?: appDatabase.issueDao().getStubForSectionImageName(image.name)
            ?: throw NotFoundException("IssueStub for image ${image.name} not found in database")
    }

    suspend fun getIssue(issueStub: IssueStub): Issue {
        return issueStubToIssue(issueStub)
    }

    fun getDownloadedIssuesCountFlow(): Flow<Int> {
        return appDatabase.issueDao().getDownloadedIssuesCountFlow()
    }

    suspend fun getAllIssueStubs(): List<IssueStub> {
        return appDatabase.issueDao().getAllIssueStubs()
    }

    suspend fun getAllDownloadedIssueStubs(): List<IssueStub> {
        return appDatabase.issueDao().getAllDownloadedIssueStubs()
    }

    suspend fun getAllPublicAndDemoIssueStubs(): List<IssueStub> {
        return appDatabase.issueDao().getAllPublicAndDemoIssueStubs()
    }

    suspend fun getMostValuableIssueStubForPublication(
        issuePublication: AbstractIssuePublication
    ): IssueStub? {
        return appDatabase.issueDao()
            .getByFeedAndDate(issuePublication.feedName, issuePublication.date)
            .maxByOrNull {
                it.status
            }
    }

    suspend fun getMostValuableIssueKeyForPublication(
        issuePublication: AbstractIssuePublication
    ): AbstractIssueKey? {
        return getMostValuableIssueStubForPublication(issuePublication)
            ?.let { if (issuePublication is IssuePublicationWithPages) IssueKeyWithPages(it.issueKey) else it.issueKey }
    }

    suspend fun delete(issueKey: IssueKey) {
        get(issueKey)?.let { delete(it) }
    }

    suspend fun delete(issue: Issue) {
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
        pageRepository.deleteIfNoIssueRelated(issue.pageList)

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

interface AbstractCoverPublication : ObservableDownload, Parcelable {
    val feedName: String
    val date: String
}

interface AbstractCoverKey : AbstractCoverPublication {
    override val feedName: String
    override val date: String
    val status: IssueStatus
}

interface AbstractIssuePublication : ObservableDownload, Parcelable {
    val feedName: String
    val date: String
}

interface AbstractIssueKey : AbstractIssuePublication {
    override val feedName: String
    override val date: String
    val status: IssueStatus
}


/**
 * An [IssuePublication] is the description of an Issue released at a certain [date] in a [feed],
 * omitting the specification of an [IssueStatus]
 */
@Parcelize
data class IssuePublication(
    override val feedName: String,
    override val date: String
) : AbstractIssuePublication {
    constructor(issueKey: AbstractIssuePublication) : this(
        issueKey.feedName,
        issueKey.date
    )

    constructor(coverPublication: AbstractCoverPublication) : this(
        coverPublication.feedName,
        coverPublication.date
    )

    constructor(articleStub: ArticleStub) : this(articleStub.issueFeedName, articleStub.issueDate)
    constructor(article: Article) : this(article.issueFeedName, article.issueDate)

    override fun getDownloadTag(): String {
        return "$feedName/$date"
    }
}

/**
 * An [IssuePublicationWithPages] is the description of an Issue released at a certain [date] in a [feedName],
 * omitting the specification of an [IssueStatus]. It's referring to the publication including classic "pdf" pages
 */
@Parcelize
data class IssuePublicationWithPages(
    override val feedName: String,
    override val date: String
) : AbstractIssuePublication {
    constructor(abstractIssuePublication: AbstractIssuePublication) : this(
        abstractIssuePublication.feedName,
        abstractIssuePublication.date
    )

    override fun getDownloadTag(): String {
        return "$feedName/$date/pdf"
    }
}

/**
 * The representation of a [feedName], [date] and [status] determining the exact
 * identity of an issue
 */
@Parcelize
data class IssueKey(
    override val feedName: String,
    override val date: String,
    override val status: IssueStatus
) : Parcelable, AbstractIssueKey {

    constructor(abstractIssueKey: AbstractIssueKey) : this(
        abstractIssueKey.feedName,
        abstractIssueKey.date,
        abstractIssueKey.status
    )

    constructor(issuePublication: AbstractIssuePublication, status: IssueStatus) : this(
        issuePublication.feedName,
        issuePublication.date,
        status
    )

    override fun getDownloadTag(): String {
        return "$feedName/$date/$status"
    }
}

@Parcelize
data class IssueKeyWithPages(
    override val feedName: String,
    override val date: String,
    override val status: IssueStatus
) : Parcelable, AbstractIssueKey {
    constructor(issueKey: IssueKey) : this(
        issueKey.feedName,
        issueKey.date,
        issueKey.status
    )

    override fun getDownloadTag(): String {
        return "$feedName/$date/$status/pdf"
    }

    fun getIssueKey() = IssueKey(feedName, date, status)
}

/**
 * The representation of a cover publication in form of [feedName] and [date]
 */
@Parcelize
data class MomentPublication(
    override val feedName: String,
    override val date: String
) : AbstractCoverPublication {
    override fun getDownloadTag(): String {
        return "$feedName/$date/moment"
    }
}

/**
 * The representation of a frontpage publication in form of [feedName] and [date]
 */
@Parcelize
data class FrontpagePublication(
    override val feedName: String,
    override val date: String
) : AbstractCoverPublication {
    override fun getDownloadTag(): String {
        return "$feedName/$date/frontpage"
    }
}

/**
 * The representation of a [feedName], [date] and [status] determining the exact
 * identity of a moment
 */
@Parcelize
data class MomentKey(
    override val feedName: String,
    override val date: String,
    override val status: IssueStatus
) : AbstractCoverKey {

    override fun getDownloadTag(): String {
        return "$feedName/$date/$status/moment"
    }
}

/**
 * The representation of a [feedName], [date] and [status] determining the exact
 * identity of a moment
 */
@Parcelize
data class FrontPageKey(
    override val feedName: String,
    override val date: String,
    override val status: IssueStatus
) : AbstractCoverKey {

    override fun getDownloadTag(): String {
        return "$feedName/$date/$status/frontpage"
    }
}

