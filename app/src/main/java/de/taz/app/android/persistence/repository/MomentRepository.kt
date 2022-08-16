package de.taz.app.android.persistence.repository

import android.content.Context
import android.database.sqlite.SQLiteConstraintException
import de.taz.app.android.api.interfaces.IssueOperations
import de.taz.app.android.api.models.*
import de.taz.app.android.persistence.join.*
import de.taz.app.android.util.SingletonHolder
import java.util.*

class MomentRepository private constructor(applicationContext: Context) :
    RepositoryBase(applicationContext) {

    companion object : SingletonHolder<MomentRepository, Context>(::MomentRepository)

    private val imageRepository = ImageRepository.getInstance(applicationContext)
    private val fileEntryRepository = FileEntryRepository.getInstance(applicationContext)

    suspend fun update(momentStub: MomentStub) {
        appDatabase.momentDao().insertOrReplace(momentStub)
    }

    suspend fun save(moment: Moment): Moment {
        imageRepository.save(moment.imageList)
        imageRepository.save(moment.creditList)
        fileEntryRepository.save(moment.momentList)
        appDatabase.momentDao().insertOrReplace(MomentStub(moment))
        appDatabase.momentImageJoinJoinDao().insertOrReplace(
            moment.imageList.mapIndexed { index, image ->
                MomentImageJoin(moment.issueFeedName, moment.issueDate, moment.issueStatus, image.name, index)
            }
        )
        appDatabase.momentCreditJoinDao().insertOrReplace(
            moment.creditList.mapIndexed { index, image ->
                MomentCreditJoin(moment.issueFeedName, moment.issueDate, moment.issueStatus, image.name, index)
            }
        )
        appDatabase.momentFilesJoinDao().insertOrReplace(
            moment.momentList.mapIndexed { index, file ->
                MomentFilesJoin(moment.issueFeedName, moment.issueDate, moment.issueStatus, file.name, index)
            }
        )
        return get(moment.momentKey)!!
    }

    suspend fun momentStubToMoment(momentStub: MomentStub): Moment {
        return Moment(
            momentStub.issueFeedName,
            momentStub.issueDate,
            momentStub.issueStatus,
            momentStub.baseUrl,
            appDatabase.momentImageJoinJoinDao().getMomentFiles(
                momentStub.issueFeedName,
                momentStub.issueDate,
                momentStub.issueStatus
            ),
            appDatabase.momentCreditJoinDao().getMomentFiles(
                momentStub.issueFeedName,
                momentStub.issueDate,
                momentStub.issueStatus
            ),
            appDatabase.momentFilesJoinDao().getMomentFiles(
                momentStub.issueFeedName,
                momentStub.issueDate,
                momentStub.issueStatus
            ),
            momentStub.dateDownload
        )
    }

    @Throws(NotFoundException::class)
    suspend fun get(issueFeedName: String, issueDate: String, issueStatus: IssueStatus): Moment? {
        val stub = appDatabase.momentDao().get(issueFeedName, issueDate, issueStatus)
        return stub?.let {
            Moment(
                stub.issueFeedName,
                stub.issueDate,
                stub.issueStatus,
                stub.baseUrl,
                appDatabase.momentImageJoinJoinDao().getMomentFiles(
                    issueFeedName,
                    issueDate,
                    issueStatus
                ),
                appDatabase.momentCreditJoinDao().getMomentFiles(
                    issueFeedName,
                    issueDate,
                    issueStatus
                ),
                appDatabase.momentFilesJoinDao().getMomentFiles(
                    issueFeedName,
                    issueDate,
                    issueStatus
                ),
                stub.dateDownload
            )
        }
    }

    suspend fun getDownloadDate(moment: Moment): Date? {
        return appDatabase.momentDao()
            .getDownloadDate(moment.issueFeedName, moment.issueDate, moment.issueStatus)
    }

    suspend fun setDownloadDate(moment: Moment, date: Date?) {
        return update(MomentStub(moment).copy(dateDownload = date))
    }

    suspend fun getStub(issueFeedName: String, issueDate: String, issueStatus: IssueStatus): MomentStub? {
        return try {
            appDatabase.momentDao().get(issueFeedName, issueDate, issueStatus)
        } catch (e: NotFoundException) {
            null
        }
    }

    suspend fun isDownloaded(momentKey: MomentKey): Boolean {
        return getStub(momentKey.feedName, momentKey.date, momentKey.status)?.dateDownload != null
    }

    suspend fun get(issueOperations: IssueOperations): Moment? {
        return get(issueOperations.feedName, issueOperations.date, issueOperations.status)
    }

    suspend fun get(momentKey: MomentKey): Moment? {
        return get(momentKey.feedName, momentKey.date, momentKey.status)
    }

    suspend fun exists(momentKey: MomentKey): Boolean {
        return get(momentKey) != null
    }

    suspend fun deleteMoment(issueFeedName: String, issueDate: String, issueStatus: IssueStatus) {
        val moment = get(issueFeedName, issueDate, issueStatus)
        moment?.let {
            appDatabase.momentImageJoinJoinDao().delete(
                moment.imageList.mapIndexed { index, fileEntry ->
                    MomentImageJoin(
                        issueFeedName,
                        issueDate,
                        issueStatus,
                        fileEntry.name,
                        index
                    )
                }
            )
            appDatabase.momentCreditJoinDao().delete(
                moment.creditList.mapIndexed { index, fileEntry ->
                    MomentCreditJoin(
                        issueFeedName,
                        issueDate,
                        issueStatus,
                        fileEntry.name,
                        index
                    )
                }
            )
            appDatabase.momentFilesJoinDao().delete(
                moment.momentList.mapIndexed { index, fileEntry ->
                    MomentFilesJoin(
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
}