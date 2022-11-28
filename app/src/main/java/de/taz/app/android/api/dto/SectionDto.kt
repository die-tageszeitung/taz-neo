package de.taz.app.android.api.dto

import kotlinx.serialization.Serializable

@Serializable
data class SectionDto (
    val sectionHtml: FileEntryDto,
    val title: String,
    val type: SectionTypeDto,
    val articleList: List<ArticleDto>? = null,
    val imageList: List<ImageDto>? = null,
    val extendedTitle: String? = null,
    val navButton: ImageDto
)