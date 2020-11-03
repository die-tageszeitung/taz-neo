package de.taz.app.android.persistence.repository

import android.content.Context
import android.database.sqlite.SQLiteConstraintException
import androidx.lifecycle.LiveData
import androidx.lifecycle.Transformations
import de.taz.app.android.api.interfaces.IssueOperations
import de.taz.app.android.api.models.*
import de.taz.app.android.persistence.join.IssueCreditMomentJoin
import de.taz.app.android.persistence.join.IssueFilesMomentJoin
import de.taz.app.android.persistence.join.IssueImageMomentJoin
import de.taz.app.android.util.SingletonHolder
import java.util.*

class MomentRepository private constructor(applicationContext: Context) :
    RepositoryBase(applicationContext) {

    companion object : SingletonHolder<MomentRepository, Context>(::MomentRepository)

    private val imageRepository = ImageRepository.getInstance(applicationContext)
    private val fileEntryRepository = FileEntryRepository.getInstance(applicationContext)

    fun update(momentStub: MomentStub) {
        appDatabase.momentDao().insertOrReplace(momentStub)
    }

    fun save(moment: Moment, issueFeedName: String, issueDate: String, issueStatus: IssueStatus) {
        imageRepository.save(moment.imageList)
        imageRepository.save(moment.creditList)
        fileEntryRepository.save(moment.momentList)
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
        appDatabase.issueFilesMomentJoinDao().insertOrReplace(
            moment.momentList.mapIndexed { index, file ->
                IssueFilesMomentJoin(issueFeedName, issueDate, issueStatus, file.name, index)
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
            appDatabase.issueFilesMomentJoinDao().getMomentFiles(
                momentStub.issueFeedName,
                momentStub.issueDate,
                momentStub.issueStatus
            ),
            null
        )
    }

    @Throws(NotFoundException::class)
    fun get(issueFeedName: String, issueDate: String, issueStatus: IssueStatus): Moment {
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
            appDatabase.issueFilesMomentJoinDao().getMomentFiles(
                issueFeedName,
                issueDate,
                issueStatus
            ),
            null
        )
    }

    fun getDownloadDate(moment: Moment): Date? {
        return appDatabase.momentDao()
            .getDownloadDate(moment.issueFeedName, moment.issueDate, moment.issueStatus)
    }

    fun setDownloadDate(moment: Moment, date: Date?) {
        return update(MomentStub(moment).copy(dateDownload = date))
    }

    fun getStub(issueFeedName: String, issueDate: String, issueStatus: IssueStatus): MomentStub? {
        return try {
            appDatabase.momentDao().get(issueFeedName, issueDate, issueStatus)
        } catch (e: NotFoundException) {
            null
        }
    }

    fun get(issueOperations: IssueOperations): Moment {
        return get(issueOperations.feedName, issueOperations.date, issueOperations.status)
    }

    fun getLiveData(issueOperations: IssueOperations): LiveData<Moment?> {
        return Transformations.map(
            appDatabase.momentDao().getLiveData(issueOperations)
        ) { momentStub ->
            momentStub?.let { momentStubToMoment(momentStub) }
        }
    }

    fun deleteMoment(issueFeedName: String, issueDate: String, issueStatus: IssueStatus) {
        val moment = get(issueFeedName, issueDate, issueStatus)
        appDatabase.issueImageMomentJoinDao().delete(
            moment.imageList.mapIndexed { index, fileEntry ->
                IssueImageMomentJoin(
                    issueFeedName,
                    issueDate,
                    issueStatus,
                    fileEntry.name,
                    index
                )
            }
        )
        appDatabase.issueCreditMomentJoinDao().delete(
            moment.creditList.mapIndexed { index, fileEntry ->
                IssueCreditMomentJoin(
                    issueFeedName,
                    issueDate,
                    issueStatus,
                    fileEntry.name,
                    index
                )
            }
        )
        appDatabase.issueFilesMomentJoinDao().delete(
            moment.momentList.mapIndexed { index, fileEntry ->
                IssueFilesMomentJoin(
                    issueFeedName,
                    issueDate,
                    issueStatus,
                    fileEntry.name,
                    index
                )
            }
        )
        try {
            imageRepository.delete(moment.imageList)
        } catch (e: SQLiteConstraintException) {
            log.warn("FileEntry ${moment.imageList} not deleted, maybe still used by another issue?")
            // do not delete is used by another issue
        }
        appDatabase.momentDao().delete(MomentStub(moment))
    }
}