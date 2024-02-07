package de.taz.app.android.persistence.repository

import android.content.Context
import android.database.sqlite.SQLiteConstraintException
import androidx.room.withTransaction
import de.taz.app.android.api.interfaces.IssueOperations
import de.taz.app.android.api.models.Issue
import de.taz.app.android.api.models.IssueStatus
import de.taz.app.android.api.models.Moment
import de.taz.app.android.api.models.MomentStub
import de.taz.app.android.persistence.join.MomentCreditJoin
import de.taz.app.android.persistence.join.MomentFilesJoin
import de.taz.app.android.persistence.join.MomentImageJoin
import de.taz.app.android.util.SingletonHolder
import java.util.Date

class MomentRepository private constructor(applicationContext: Context) :
    RepositoryBase(applicationContext) {

    companion object : SingletonHolder<MomentRepository, Context>(::MomentRepository)

    private val imageRepository = ImageRepository.getInstance(applicationContext)
    private val fileEntryRepository = FileEntryRepository.getInstance(applicationContext)


    suspend fun update(momentStub: MomentStub) {
        appDatabase.momentDao().insertOrReplace(momentStub)
    }

    /**
     * Save the [Moment] to the database and replace any existing [Moment] with the same key.
     *
     * This method must be called as part of a transaction,
     * for example when saving an [Issue]
     * or when downloading a single [Moment] for displaying within the cover view.
     *
     * As there are many-to-many relations, replacing an existing [Moment] might result in some orphaned
     * children that have to be cleanup up by some scrubber process.
     */
    suspend fun saveInternal(moment: Moment) {
        imageRepository.saveInternal(moment.imageList)
        imageRepository.saveInternal(moment.creditList)
        fileEntryRepository.save(moment.momentList)
        appDatabase.momentDao().insertOrReplace(MomentStub(moment))

        appDatabase.momentImageJoinJoinDao().apply {
            deleteRelationToMoment(moment.issueFeedName, moment.issueDate, moment.issueStatus)
            insertOrAbort(moment.imageList.mapIndexed { index, image ->
                MomentImageJoin(
                    moment.issueFeedName,
                    moment.issueDate,
                    moment.issueStatus,
                    image.name,
                    index
                )
            })
        }
        appDatabase.momentCreditJoinDao().apply {
            deleteRelationToMoment(moment.issueFeedName, moment.issueDate, moment.issueStatus)
            insertOrAbort(moment.creditList.mapIndexed { index, image ->
                MomentCreditJoin(
                    moment.issueFeedName,
                    moment.issueDate,
                    moment.issueStatus,
                    image.name,
                    index
                )
            })
        }
        appDatabase.momentFilesJoinDao().apply {
            deleteRelationToMoment(moment.issueFeedName, moment.issueDate, moment.issueStatus)
            insertOrAbort(
                moment.momentList.mapIndexed { index, file ->
                    MomentFilesJoin(
                        moment.issueFeedName,
                        moment.issueDate,
                        moment.issueStatus,
                        file.name,
                        index
                    )
                }
            )
        }
    }

    /**
     * Save a single downloaded [Moment].
     * The [Moment] may exist without a related [Issue].
     */
    suspend fun save(moment: Moment): Moment {
        return appDatabase.withTransaction {
            saveInternal(moment)
            requireNotNull(get(moment.momentKey)) { "Could not get Moment(${moment.momentKey}) after it was saved" }
        }
    }

    private suspend fun momentStubToMoment(momentStub: MomentStub): Moment {
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

    suspend fun get(issueFeedName: String, issueDate: String, issueStatus: IssueStatus): Moment? {
        val stub = appDatabase.momentDao().get(issueFeedName, issueDate, issueStatus)
        return stub?.let { momentStubToMoment(it) }
    }

    suspend fun getDownloadDate(moment: Moment): Date? {
        return appDatabase.momentDao()
            .getDownloadDate(moment.issueFeedName, moment.issueDate, moment.issueStatus)
    }

    suspend fun setDownloadDate(moment: Moment, date: Date?) {
        return update(MomentStub(moment).copy(dateDownload = date))
    }

    suspend fun getStub(issueFeedName: String, issueDate: String, issueStatus: IssueStatus): MomentStub? {
        return appDatabase.momentDao().get(issueFeedName, issueDate, issueStatus)
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

    suspend fun get(issueKey: AbstractIssueKey): Moment? =
        get(issueKey.feedName, issueKey.date, issueKey.status)

    suspend fun exists(momentKey: MomentKey): Boolean {
        return getStub(momentKey.feedName, momentKey.date, momentKey.status) != null
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