package de.taz.app.android.api.dto

import kotlinx.serialization.Serializable

@Serializable
data class FeedDto (
    val name: String? = null,
    val cycle: Cycle? = null,
    val momentRatio: Float? = null,
    val publicationDates: List<String> = emptyList(),
    val issueCnt: Int? = null,
    val issueMaxDate: String? = null,
    val issueMinDate: String? = null,
    val issueList: List<IssueDto>? = null
)

@Serializable
enum class Cycle {
    daily,
    weekly,
    monthly,
    quarterly,
    yearly
}