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
    val zipName: String? = null,
    val zipPdfName: String? = null,
    val navButton: NavButtonDto? = null,
    val imprint: ArticleDto?,
    val fileList: List<String>,
    val fileListPdf: List<String>?,
    val sectionList: List<SectionDto>? = null,
    val pageList: List<PageDto>? = null
)
