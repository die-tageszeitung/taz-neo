package de.taz.app.android.persistence.repository

import android.content.Context
import androidx.lifecycle.LiveData
import de.taz.app.android.api.models.*
import de.taz.app.android.persistence.join.IssueImprintJoin
import de.taz.app.android.persistence.join.IssuePageJoin
import de.taz.app.android.persistence.join.IssueSectionJoin
import de.taz.app.android.util.SingletonHolder

class IssueRepository private constructor(applicationContext: Context) :
    RepositoryBase(applicationContext) {

    companion object : SingletonHolder<IssueRepository, Context>(::IssueRepository)

    private val articleRepository = ArticleRepository.getInstance(applicationContext)
    private val pageRepository = PageRepository.getInstance(applicationContext)
    private val sectionRepository = SectionRepository.getInstance(applicationContext)
    private val momentRepository = MomentRepository.getInstance(applicationContext)

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

    fun getWithoutFiles(): ResourceInfoStub? {
        return appDatabase.resourceInfoDao().get()
    }

    fun getLatestIssueBase(): IssueStub? {
        return appDatabase.issueDao().getLatest()
    }

    fun getLatestIssue(): Issue? {
        return getLatestIssueBase()?.let { issueBaseToIssue(it) }
    }

    fun getLatestIssueBaseLiveData(): LiveData<IssueStub?> {
        return appDatabase.issueDao().getLatestLiveData()
    }

    @Throws(NotFoundException::class)
    fun getLatestIssueBaseOrThrow(): IssueStub {
        return appDatabase.issueDao().getLatest() ?: throw NotFoundException()
    }

    @Throws(NotFoundException::class)
    fun getLatestIssueOrThrow(): Issue {
        return getLatestIssueBase()?.let { issueBaseToIssue(it) } ?: throw NotFoundException()
    }

    fun getIssueBaseByFeedAndDate(feedName: String, date: String): IssueStub? {
        return appDatabase.issueDao().getByFeedAndDate(feedName, date)
    }

    fun getIssueByFeedAndDate(feedName: String, date: String): Issue? {
        return getIssueBaseByFeedAndDate(feedName, date)?.let {
            issueBaseToIssue(it)
        }
    }

    fun getIssueBaseForSection(sectionFileName: String): IssueStub {
        return appDatabase.issueSectionJoinDao().getIssueBaseForSection(sectionFileName)
    }

    fun getIssueBaseForMoment(moment: Moment): IssueStub {
        return appDatabase.issueMomentJoinDao().getIssueBase(moment.imageList.first().name)
    }

    private fun issueBaseToIssue(issueStub: IssueStub): Issue {
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
        return issueBaseToIssue(issueStub)
    }

    fun delete(issue: Issue) {
        appDatabase.runInTransaction {

            // TODO cancel Downloads

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
                sectionList.forEach { sectionRepository.delete(it) }
            }


            appDatabase.issueDao().delete(
                IssueStub(issue)
            )

            // TODO actually delete files! perhaps decide if to keep some

        }
    }


}