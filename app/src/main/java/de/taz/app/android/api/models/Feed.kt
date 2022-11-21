package de.taz.app.android.api.models

import androidx.room.Entity
import androidx.room.PrimaryKey
import de.taz.app.android.api.dto.Cycle
import de.taz.app.android.api.dto.FeedDto
import de.taz.app.android.simpleDateFormat

@Entity(tableName = "Feed")
data class Feed(
    @PrimaryKey val name: String,
    val cycle: Cycle,
    val momentRatio: Float,
    val publicationDates: List<PublicationDate>,
    val issueMinDate: String,
    val issueMaxDate: String,
) {

    constructor(feedDto: FeedDto) : this(
        requireNotNull(feedDto.name),
        requireNotNull(feedDto.cycle),
        requireNotNull(feedDto.momentRatio),
        feedDto.publicationDates.let { publicationDates ->
            val validityMap = feedDto.validityDates.associate { it.date to it.validityDate }
            publicationDates.sortedDescending().map {
                PublicationDate(
                    requireNotNull(simpleDateFormat.parse(it)),
                    validityMap[it]?.let(simpleDateFormat::parse)
                )
            }
        },
        requireNotNull(feedDto.issueMinDate),
        requireNotNull(feedDto.issueMaxDate)
    )

    fun momentRatioAsDimensionRatioString(): String {
        return "$momentRatio:1"
    }
}

