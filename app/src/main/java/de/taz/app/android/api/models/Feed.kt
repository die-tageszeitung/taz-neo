package de.taz.app.android.api.models

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "Feed")
data class Feed(
    @PrimaryKey val name: String,
    val cycle: Cycle,
    val momentRatio: Float,
    val publicationDates: List<PublicationDate>,
    val issueMinDate: String,
    val issueMaxDate: String,
) {

    companion object {
        /**
         * Shallow equals method that skips the comparison of the full [publicationDates] list.
         * Two feeds [publicationDates] are considered equals when they have the same size and the same first element.
         */
        fun equalsShallow(first: Feed?, second: Feed?): Boolean {
            if (first === second) return true
            if (first == null || second == null) return false
            if (first.javaClass != second.javaClass) return false

            if (first.name != second.name) return false
            if (first.cycle != second.cycle) return false
            if (first.momentRatio != second.momentRatio) return false
            if (first.issueMinDate != second.issueMinDate) return false
            if (first.issueMaxDate != second.issueMaxDate) return false

            // Only compare the size and the the latest publication date
            if (first.publicationDates.size != second.publicationDates.size) return false
            if (first.publicationDates.firstOrNull() != second.publicationDates.firstOrNull()) return false

            return true
        }
    }

    fun momentRatioAsDimensionRatioString(): String {
        return "$momentRatio:1"
    }

    override fun toString(): String {
        val publicationDatesString = "#${publicationDates.size} (${publicationDates.firstOrNull()?.date}, ...)"
        return "Feed(name='$name', cycle=$cycle, momentRatio=$momentRatio, publicationDates=$publicationDatesString, issueMinDate='$issueMinDate', issueMaxDate='$issueMaxDate')"
    }
}

