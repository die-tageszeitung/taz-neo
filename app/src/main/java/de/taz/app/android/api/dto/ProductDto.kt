package de.taz.app.android.api.dto

import kotlinx.serialization.Serializable

@Serializable
data class ProductDto(
    val resourceVersion: Int? = null,
    val resourceBaseUrl: String? = null,
    val resourceZip: String? = null,
    val resourceList: List<FileEntryDto>? = null,
    val authInfo: AuthInfoDto? = null,
    val appType: AppTypeDto? = null,
    val appName: AppNameDto? = null,
    val globalBaseUrl: String? = null,
    val feedList: List<FeedDto>? = null,
    val androidVersion: Int? = null
)
