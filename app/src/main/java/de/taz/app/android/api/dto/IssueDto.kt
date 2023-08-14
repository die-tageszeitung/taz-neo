package de.taz.app.android.api.dto

import kotlinx.serialization.Serializable

@Serializable
class IssueDto(
    val date: String,
    val validityDate: String? = null,
    val moment: MomentDto,
    val key: String? = null,
    val baseUrl: String,
    val status: IssueStatusDto,
    val minResourceVersion: Int,
    val imprint: ArticleDto? = null,
    val isWeekend: Boolean = false,
    val sectionList: List<SectionDto>? = null,
    val pageList: List<PageDto>? = null,
    val moTime: String,
)
