package de.taz.app.android.api.dto

import com.squareup.moshi.JsonClass
import de.taz.app.android.api.models.AuthInfo

@JsonClass(generateAdapter = true)
data class SearchDto(
    val authInfo: AuthInfo,
    val sessionId: String?,
    val searchHitList: List<SearchHitDto>?,
    val total: Int,
    val totalFound: Int,
    val time: Float?,
    val offset: Int,
    val rowCnt: Int,
    val next: Int,
    val prev: Int,
    val text: String?,
    val author: String?,
    val title: String?,
    val snippetWords: String?,
    val sorting: Sorting,
    val searchTime: String?,
    val pubDateFrom: String?,
    val pubDateUntil: String?,
    val minPubDate: String
)