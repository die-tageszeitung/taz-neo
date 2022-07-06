package de.taz.app.android.api.dto

import kotlinx.serialization.Serializable
import de.taz.app.android.annotation.Mockable
import de.taz.app.android.api.models.*

@Serializable
@Mockable
class IssueDto(
    val date: String,
    val moment: MomentDto,
    val key: String? = null,
    val baseUrl: String,
    val status: IssueStatus,
    val minResourceVersion: Int,
    val imprint: ArticleDto? = null,
    val isWeekend: Boolean = false,
    val sectionList: List<SectionDto>? = null,
    val pageList: List<PageDto>? = null,
    val moTime: String
)
