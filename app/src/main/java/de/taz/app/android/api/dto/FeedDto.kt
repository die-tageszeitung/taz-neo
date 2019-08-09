package de.taz.app.android.api.dto

data class FeedDto (
    val name: String? = null,
    val cycle: Cycle? = null,
    val momentRatio: Float? = null,
    val issueCnt: Int? = null,
    val issueMaxDate: String? = null,
    val issueMinDate: String? = null,
    val issueList: List<IssueDto>? = null
)

enum class Cycle {
    daily,
    weekly,
    monthly,
    quarterly,
    yearly
}