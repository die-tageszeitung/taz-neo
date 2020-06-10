package de.taz.app.android.persistence.repository

import android.content.Context
import android.database.sqlite.SQLiteConstraintException
import androidx.lifecycle.LiveData
import androidx.lifecycle.Transformations
import de.taz.app.android.api.interfaces.IssueOperations
import de.taz.app.android.api.models.*
import de.taz.app.android.persistence.join.IssueMomentJoin
import de.taz.app.android.util.SingletonHolder

class MomentRepository private constructor(applicationContext: Context) :
    RepositoryBase(applicationContext) {

    companion object : SingletonHolder<MomentRepository, Context>(::MomentRepository)

    private val imageRepository = ImageRepository.getInstance()

    fun save(moment: Moment, issueFeedName: String, issueDate: String, issueStatus: IssueStatus) {
        imageRepository.save(moment.imageList)
        imageRepository.save(moment.creditList)
        appDatabase.issueMomentJoinDao().insertOrReplace(
            moment.imageList.mapIndexed { index, fileEntry ->
                IssueMomentJoin(issueFeedName, issueDate, issueStatus, fileEntry.name, index)
            }
        )
    }

    @Throws(NotFoundException::class)
    fun getOrThrow(issueFeedName: String, issueDate: String, issueStatus: IssueStatus): Moment {
        return Moment(
            appDatabase.issueMomentJoinDao().getMomentFiles(
                issueFeedName,
                issueDate,
                issueStatus
            )
        )
    }

    @Throws(NotFoundException::class)
    fun getOrThrow(issueOperations: IssueOperations): Moment {
        return getOrThrow(issueOperations.feedName, issueOperations.date, issueOperations.status)
    }

    fun get(issueFeedName: String, issueDate: String, issueStatus: IssueStatus): Moment? {
        return try {
            getOrThrow(issueFeedName, issueDate, issueStatus)
        } catch (e: NotFoundException) {
            null
        }
    }

    fun get(issueOperations: IssueOperations): Moment? {
        return get(issueOperations.feedName, issueOperations.date, issueOperations.status)
    }

    fun getLiveData(issueOperations: IssueOperations): LiveData<Moment?> {
        return Transformations.map(
            appDatabase.issueMomentJoinDao().getMomentFilesLiveData(
                issueOperations.feedName,
                issueOperations.date,
                issueOperations.status
            )
        ) { imageList ->
            Moment(imageList)
        }

    }

    fun getByImageName(imageName: String): Moment? {
        return Moment(appDatabase.issueMomentJoinDao().getByImageName(imageName))
    }

    fun delete(moment: Moment, issueFeedName: String, issueDate: String, issueStatus: IssueStatus) {
        appDatabase.issueMomentJoinDao().delete(
            moment.imageList.mapIndexed { index, fileEntry ->
                IssueMomentJoin(issueFeedName, issueDate, issueStatus, fileEntry.name, index)
            }
        )
        try {
            imageRepository.delete(moment.imageList)
            log.debug("deleted FileEntry of image ${moment.imageList}")
        } catch (e: SQLiteConstraintException) {
            log.warn("FileEntry ${moment.imageList} not deleted, maybe still used by another issue?")
            // do not delete is used by another issue
        }
    }
}