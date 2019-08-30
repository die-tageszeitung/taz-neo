package de.taz.app.android.persistence.repository

import androidx.room.Transaction
import de.taz.app.android.api.models.*
import de.taz.app.android.persistence.AppDatabase
import de.taz.app.android.persistence.join.IssueImprintJoin
import de.taz.app.android.persistence.join.IssuePageJoin
import de.taz.app.android.persistence.join.IssueSectionJoin
import kotlinx.coroutines.internal.artificialFrame

object IssueRepository {

    private val appDatabase = AppDatabase.getInstance()

    @Transaction
    fun save(issue: Issue) {
        appDatabase.issueDao().insertOrReplace(
            IssueBase(issue)
        )
        // save pages
        appDatabase.pageDao().insertOrReplace(
            issue.pageList?.map { PageWithoutFile(it) } ?: listOf()
        )
        // save page relation
        appDatabase.issuePageJoinDao().insertOrReplace(
            issue.pageList?.map {
                IssuePageJoin(issue.feedName, issue.date, it.pagePdf.name)
            } ?: listOf()
        )

        // save imprint
        appDatabase.issueImprintJoinDao().insertOrReplace(
            IssueImprintJoin(issue.feedName, issue.date, issue.imprint.articleHtml.name)
        )

        // save sections
        issue.sectionList?.let { sectionList ->
            appDatabase.sectionDao().insertOrReplace(sectionList.map {
                SectionBase(it.sectionHtml.name, it.title, it.type)
            })
            appDatabase.issueSectionJoinDao().insertOrReplace(sectionList.map {
                IssueSectionJoin(issue.feedName, issue.date, it.sectionHtml.name)
            })
        }
    }

    fun getWithoutFiles(): ResourceInfoWithoutFiles {
        return appDatabase.resourceInfoDao().get()
    }

    fun getLatestIssueBase(): IssueBase {
        return appDatabase.issueDao().getLatest()
    }

    fun getLatestIssue(): Issue {

    }

    fun getIssueByFeedAndDate(feedName: String, date: String): IssueBase {
        return appDatabase.issueDao().getByFeedAndDate(feedName, date)
    }

    private fun issueBaseToIssue(issueBase: IssueBase): Issue {
        val sectionNames = appDatabase.issueSectionJoinDao().getSectionNamesForIssue(issueBase)
        val sections = sectionNames?.map { SectionRepository.get(it) }

        val imprint = ArticleRepository.get(
            appDatabase.issueImprintJoinDao().getImprintNameForIssue(
                issueBase.feedName, issueBase.date
            )
        )

        val pageList =
            appDatabase.issuePageJoinDao().getPageNamesForIssue(issueBase.feedName, issueBase.date)
                ?.map {
                    PageRepository.get(it)
                }

        return Issue(
            issueBase.feedName,
            issueBase.date,
            issueBase.key,
            issueBase.baseUrl,
            issueBase.status,
            issueBase.minResourceVersion,
            issueBase.zipName,
            issueBase.zipPdfName,
            issueBase.navButton,
            imprint,
            issueBase.fileList,
            issueBase.fileListPdf,
            sections,
            pageList
        )

    }


}