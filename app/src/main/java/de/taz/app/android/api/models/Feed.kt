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

    fun momentRatioAsDimensionRatioString(): String {
        return "$momentRatio:1"
    }
}

