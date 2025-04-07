package de.taz.app.android.persistence.repository

import android.content.Context
import de.taz.app.android.api.dto.BookmarkRepresentation
import de.taz.app.android.api.models.BookmarkSynchronization
import de.taz.app.android.api.models.SynchronizeFromType
import de.taz.app.android.util.SingletonHolder
import java.util.Date

class BookmarkSynchronizationRepository private constructor(applicationContext: Context) :
    RepositoryBase(applicationContext) {
    companion object :
        SingletonHolder<BookmarkSynchronizationRepository, Context>(::BookmarkSynchronizationRepository)

    suspend fun save(
        bookmarkRepresentation: BookmarkRepresentation,
        from: SynchronizeFromType,
        localBookmarkedDate: Date? = null,
        synchronizedDate: Date? = null,
    ) {
        val bookmarkSynchronization = BookmarkSynchronization(
            bookmarkRepresentation.mediaSyncId,
            bookmarkRepresentation.date,
            from,
            localBookmarkedDate,
            synchronizedDate
        )
        appDatabase.bookmarkSynchronizationDao().insertOrReplace(bookmarkSynchronization)
    }

    suspend fun markAsSynchronized(bookmarkRepresentation: BookmarkRepresentation) {
        val bookmarkSynchronization = get(bookmarkRepresentation.mediaSyncId) ?: return
        bookmarkSynchronization.setSynchronized()
        appDatabase.bookmarkSynchronizationDao().update(bookmarkSynchronization)
    }

    suspend fun markAsLocallyChanged(mediaSyncId: Int) {
        val bookmarkSynchronization = get(mediaSyncId) ?: return
        bookmarkSynchronization.setLocallyChanged()
        appDatabase.bookmarkSynchronizationDao().update(bookmarkSynchronization)
    }

    suspend fun get(mediaSyncId: Int): BookmarkSynchronization? {
        return appDatabase.bookmarkSynchronizationDao().get(mediaSyncId)
    }

    suspend fun delete(mediaSyncId: Int) {
        val bookmarkSynchronization = get(mediaSyncId) ?: return
        appDatabase.bookmarkSynchronizationDao().delete(bookmarkSynchronization)
    }
}