package de.taz.app.android.persistence.repository

import android.content.Context
import android.database.sqlite.SQLiteConstraintException
import androidx.lifecycle.LiveData
import androidx.lifecycle.Transformations
import de.taz.app.android.api.interfaces.IssueOperations
import de.taz.app.android.api.models.*
import de.taz.app.android.persistence.join.IssueCreditMomentJoin
import de.taz.app.android.persistence.join.IssueImageMomentJoin
import de.taz.app.android.util.SingletonHolder

class MomentRepository private constructor(applicationContext: Context) :
    RepositoryBase(applicationContext) {

    companion object : SingletonHolder<MomentRepository, Context>(::MomentRepository)

    private val imageRepository = ImageRepository.getInstance(applicationContext)

    fun update(momentStub: MomentStub) {
        appDatabase.momentDao().insertOrReplace(momentStub)
    }

    fun save(moment: Moment, issueFeedName: String, issueDate: String, issueStatus: IssueStatus) {
        imageRepository.save(moment.imageList)
        imageRepository.save(moment.creditList)
        appDatabase.momentDao().insertOrReplace(MomentStub(moment))
        appDatabase.issueImageMomentJoinDao().insertOrReplace(
            moment.imageList.mapIndexed { index, image ->
                IssueImageMomentJoin(issueFeedName, issueDate, issueStatus, image.name, index)
            }
        )
        appDatabase.issueCreditMomentJoinDao().insertOrReplace(
            moment.creditList.mapIndexed { index, image ->
                IssueCreditMomentJoin(issueFeedName, issueDate, issueStatus, image.name, index)
            }
        )
    }

    fun momentStubToMoment(momentStub: MomentStub): Moment {
        return Moment(
            momentStub.issueFeedName,
            momentStub.issueDate,
            momentStub.issueStatus,
            appDatabase.issueImageMomentJoinDao().getMomentFiles(
                momentStub.issueFeedName,
                momentStub.issueDate,
                momentStub.issueStatus
            ),
            appDatabase.issueCreditMomentJoinDao().getMomentFiles(
                momentStub.issueFeedName,
                momentStub.issueDate,
                momentStub.issueStatus
            ),
            momentStub.downloadedStatus
        )
    }

    @Throws(NotFoundException::class)
    fun getOrThrow(issueFeedName: String, issueDate: String, issueStatus: IssueStatus): Moment {
        return Moment(
            issueFeedName,
            issueDate,
            issueStatus,
            appDatabase.issueImageMomentJoinDao().getMomentFiles(
                issueFeedName,
                issueDate,
                issueStatus
            ),
            appDatabase.issueCreditMomentJoinDao().getMomentFiles(
                issueFeedName,
                issueDate,
                issueStatus
            ),
            appDatabase.momentDao().getDownloadStatus(issueFeedName, issueDate, issueStatus)
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
            appDatabase.momentDao().getLiveData(issueOperations)
        ) { momentStub ->
            momentStub?.let { momentStubToMoment(momentStub) }
        }
    }

    fun getDownloadedStatus(
        issueFeedName: String,
        issueDate: String,
        issueStatus: IssueStatus
    ): DownloadStatus? {
        return appDatabase.momentDao().getDownloadStatus(
            issueFeedName, issueDate, issueStatus
        )
    }

    fun deleteMoment(moment: Moment, issueFeedName: String, issueDate: String, issueStatus: IssueStatus) {
        appDatabase.issueImageMomentJoinDao().delete(
            moment.imageList.mapIndexed { index, fileEntry ->
                IssueImageMomentJoin(issueFeedName, issueDate, issueStatus, fileEntry.name, index)
            }
        )
        appDatabase.issueCreditMomentJoinDao().delete(
            moment.creditList.mapIndexed { index, fileEntry ->
                IssueCreditMomentJoin(issueFeedName, issueDate, issueStatus, fileEntry.name, index)
            }
        )
        try {
            imageRepository.delete(moment.imageList)
            log.debug("deleted FileEntry of image ${moment.imageList}")
        } catch (e: SQLiteConstraintException) {
            log.warn("FileEntry ${moment.imageList} not deleted, maybe still used by another issue?")
            // do not delete is used by another issue
        }
        appDatabase.momentDao().delete(MomentStub(moment))
    }
}