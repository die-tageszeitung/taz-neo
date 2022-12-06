package de.taz.app.android.api.mappers

import de.taz.app.android.api.dto.FeedDto
import de.taz.app.android.api.models.Feed
import de.taz.app.android.api.models.PublicationDate
import de.taz.app.android.simpleDateFormat

object FeedMapper {
    fun from(feedDto: FeedDto): Feed {
        return Feed(
            requireNotNull(feedDto.name),
            CycleMapper.from(requireNotNull(feedDto.cycle)),
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
    }
}