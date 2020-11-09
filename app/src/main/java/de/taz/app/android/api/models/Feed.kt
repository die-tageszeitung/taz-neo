package de.taz.app.android.api.models

import androidx.room.Entity
import androidx.room.PrimaryKey
import de.taz.app.android.api.dto.Cycle
import de.taz.app.android.api.dto.FeedDto
import de.taz.app.android.simpleDateFormat
import java.util.*

@Entity(tableName = "Feed")
data class Feed(
    @PrimaryKey val name: String,
    val cycle: Cycle,
    val momentRatio: Float,
    val publicationDates: List<Date>,
    val issueMinDate: String,
    val issueMaxDate: String
) {
    constructor(feedDto: FeedDto) : this(
        feedDto.name!!,
        feedDto.cycle!!,
        feedDto.momentRatio!!,
        feedDto.publicationDates.map(simpleDateFormat::parse).sortedDescending(),
        feedDto.issueMinDate!!,
        feedDto.issueMaxDate!!
    )

    fun momentRatioAsDimensionRatioString(): String {
        return "$momentRatio:1"
    }
}

