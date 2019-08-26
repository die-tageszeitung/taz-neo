package de.taz.app.android.persistence.repository

import de.taz.app.android.api.models.*
import de.taz.app.android.persistence.AppDatabase
import de.taz.app.android.persistence.join.ResourceInfoFileEntryJoin

object IssueRepository {

    private val appDatabase = AppDatabase.getInstance()

    fun save(issue: Issue) {
        appDatabase.issueDao().insertOrReplace(
            IssueBase(issue)
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