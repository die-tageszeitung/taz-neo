package de.taz.app.android.persistence.repository

import androidx.room.Transaction
import de.taz.app.android.api.models.*
import de.taz.app.android.persistence.AppDatabase
import de.taz.app.android.persistence.join.IssuePageJoin
import de.taz.app.android.persistence.join.ResourceInfoFileEntryJoin

object IssueRepository {

    private val appDatabase = AppDatabase.getInstance()

    @Transaction
    fun save(issue: Issue) {
        appDatabase.issueDao().insertOrReplace(
            IssueBase(issue)
        )
        // save pages
        appDatabase.pageDao().insertOrReplace(
            issue.pageList?.map { PageWithoutFile(it) }?: listOf()
        )
        // save page relation
        appDatabase.issuePageJoinDao().insertOrReplace(
            issue.pageList?.map {
                IssuePageJoin(issue.feedName, issue.date, it.pagePdf.name)
            } ?: listOf()
        )
        // TODO save the rest
    }

    fun getWithoutFiles(): ResourceInfoWithoutFiles {
        return appDatabase.resourceInfoDao().get()
    }

    fun getLatestIssueBase(): IssueBase {
        return appDatabase.issueDao().getLatest()
    }
}