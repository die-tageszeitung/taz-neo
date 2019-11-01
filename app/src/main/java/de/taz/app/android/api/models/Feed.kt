package de.taz.app.android.api.models

import androidx.room.Entity
import androidx.room.PrimaryKey
import de.taz.app.android.api.dto.Cycle
import de.taz.app.android.api.dto.FeedDto

@Entity(tableName = "Feed")
data class Feed(
    @PrimaryKey val name: String,
    val cycle: Cycle,
    val momentRatio: Float,
    val issueMinDate: String
) {
    constructor(feedDto: FeedDto) : this(
        feedDto.name!!,
        feedDto.cycle!!,
        feedDto.momentRatio!!,
        feedDto.issueMinDate!!
    )
}

