package de.taz.app.android.api.dto

import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class FeedDto (
    val name: String? = null,
    val cycle: Cycle? = null,
    val momentRatio: Float? = null,
    val issueCnt: Int? = null,
    val issueMaxDate: String? = null,
    val issueMinDate: String? = null,
    val issueList: List<IssueDto>? = null
)

@JsonClass(generateAdapter = false)
enum class Cycle {
    daily,
    weekly,
    monthly,
    quarterly,
    yearly
}