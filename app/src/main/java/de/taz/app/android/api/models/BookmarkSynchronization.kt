package de.taz.app.android.api.models

import androidx.room.Entity
import androidx.room.PrimaryKey
import de.taz.app.android.api.dto.BookmarkRepresentation
import java.util.Date

@Entity(
    tableName = "BookmarkSynchronization"
)
data class BookmarkSynchronization(
    @PrimaryKey val mediaSyncId: Int,
    val articleDate: String,
    val from: SynchronizeFromType,
    var locallyChangedTime: Date?,
    var synchronizedTime: Date?,
) {
    constructor(bookmarkRepresentation: BookmarkRepresentation, from: SynchronizeFromType) : this(
        bookmarkRepresentation.mediaSyncId,
        bookmarkRepresentation.date,
        from,
        null,
        null,
    )
    fun setSynchronized() {
        // set locallyChangedTime to null, as it is now synchronized
        this.locallyChangedTime = null
        this.synchronizedTime = Date()
    }

    fun setLocallyChanged() {
        this.locallyChangedTime = Date()
    }
}

enum class SynchronizeFromType { LOCAL, REMOTE }