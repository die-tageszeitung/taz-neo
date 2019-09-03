package de.taz.app.android.persistence.repository

import androidx.room.Transaction
import de.taz.app.android.api.models.*
import de.taz.app.android.persistence.AppDatabase
import de.taz.app.android.persistence.join.IssueImprintJoin
import de.taz.app.android.persistence.join.IssuePageJoin
import de.taz.app.android.persistence.join.IssueSectionJoin

class IssueRepository(private val appDatabase: AppDatabase = AppDatabase.getInstance()) {

    private val articleRepository = ArticleRepository(appDatabase)
    private val pageRepository = PageRepository(appDatabase)
    private val sectionRepository = SectionRepository(appDatabase)

    @Transaction
    fun save(issue: Issue) {
        appDatabase.issueDao().insertOrReplace(
            IssueBase(issue)
        )
        // save pages
        pageRepository.save(issue.pageList)

        // save page relation
        appDatabase.issuePageJoinDao().insertOrReplace(
            issue.pageList.map {
                IssuePageJoin(issue.feedName, issue.date, it.pagePdf.name)
            }
        )

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
        return issueBaseToIssue(getLatestIssueBase())
    }

    fun getIssueBaseByFeedAndDate(feedName: String, date: String): IssueBase {
        return appDatabase.issueDao().getByFeedAndDate(feedName, date)
    }

    fun getIssueByFeedAndDate(feedName: String, date: String): Issue {
        return  issueBaseToIssue(getIssueBaseByFeedAndDate(feedName, date))
    }

    private fun issueBaseToIssue(issueBase: IssueBase): Issue {
        val sectionNames = appDatabase.issueSectionJoinDao().getSectionNamesForIssue(issueBase)
        val sections = sectionNames.map { sectionRepository.get(it) }

        val imprint = appDatabase.issueImprintJoinDao().getImprintNameForIssue(
                issueBase.feedName, issueBase.date
            )?.let { articleRepository.get(it) }

        val pageList =
            appDatabase.issuePageJoinDao().getPageNamesForIssue(issueBase.feedName, issueBase.date)
                .map {
                    pageRepository.get(it)
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