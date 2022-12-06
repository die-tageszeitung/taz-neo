package de.taz.app.android.api.dto

import kotlinx.serialization.Serializable

@Serializable
data class FeedDto(
    val name: String? = null,
    val cycle: CycleDto? = null,
    val momentRatio: Float? = null,
    val publicationDates: List<String> = emptyList(),
    val issueCnt: Int? = null,
    val issueMaxDate: String? = null,
    val issueMinDate: String? = null,
    val issueList: List<IssueDto>? = null,
    val validityDates: List<ValidityDateDto> = emptyList()
)
