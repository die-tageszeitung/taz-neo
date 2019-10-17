package de.taz.app.android.persistence.repository

import android.content.Context
import de.taz.app.android.api.interfaces.IssueOperations
import de.taz.app.android.api.models.*
import de.taz.app.android.persistence.join.IssueMomentJoin
import de.taz.app.android.util.SingletonHolder

class MomentRepository private constructor(applicationContext: Context) :
    RepositoryBase(applicationContext) {

    companion object : SingletonHolder<MomentRepository, Context>(::MomentRepository)

    fun save(moment: Moment, issueFeedName: String, issueDate: String) {
        appDatabase.runInTransaction {
            appDatabase.fileEntryDao().insertOrReplace(moment.imageList)
            appDatabase.issueMomentJoinDao().insertOrReplace(
                moment.imageList.mapIndexed { index, fileEntry ->
                    IssueMomentJoin(issueFeedName, issueDate, fileEntry.name, index)
                }
            )
        }
    }

    @Throws(NotFoundException::class)
    fun getOrThrow(issueFeedName: String, issueDate: String): Moment {
        return Moment(appDatabase.issueMomentJoinDao().getMomentFiles(issueFeedName, issueDate))
    }

    @Throws(NotFoundException::class)
    fun getOrThrow(issue: IssueOperations): Moment {
        return getOrThrow(issue.feedName, issue.date)
    }

    fun get(issueFeedName: String, issueDate: String): Moment? {
        return try {
            getOrThrow(issueFeedName, issueDate)
        } catch (e: NotFoundException) {
            null
        }
    }

    fun get(issue: IssueOperations): Moment? {
        return get(issue.feedName, issue.date)
    }

    fun delete(moment: Moment, issueFeedName: String, issueDate: String) {
        appDatabase.runInTransaction {
            appDatabase.issueMomentJoinDao().delete(
                moment.imageList.mapIndexed { index, fileEntry ->
                    IssueMomentJoin(issueFeedName, issueDate, fileEntry.name, index)
                }
            )
            appDatabase.fileEntryDao().delete(moment.imageList)
        }
    }
}