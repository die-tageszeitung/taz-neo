package de.taz.app.android.api.dto

import com.squareup.moshi.JsonClass
import de.taz.app.android.annotation.Mockable
import de.taz.app.android.api.models.*

@JsonClass(generateAdapter = true)
@Mockable
class IssueDto(
    val date: String,
    val moment: MomentDto,
    val key: String? = null,
    val baseUrl: String,
    val status: IssueStatus,
    val minResourceVersion: Int,
    val imprint: ArticleDto?,
    val isWeekend: Boolean = false,
    val sectionList: List<SectionDto>? = null,
    val pageList: List<PageDto>? = null,
    val moTime: String
)
