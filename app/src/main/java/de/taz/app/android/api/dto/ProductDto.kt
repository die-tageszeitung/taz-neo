package de.taz.app.android.api.dto

import com.squareup.moshi.JsonClass
import de.taz.app.android.api.models.AuthInfo

@JsonClass(generateAdapter = true)
data class ProductDto (
    val resourceVersion: Int? = null,
    val resourceBaseUrl: String? = null,
    val resourceZip: String? = null,
    val resourceList: List<FileEntryDto>? = null,
    val authInfo: AuthInfo? = null,
    val appType: AppType? = null,
    val appName: AppName? = null,
    val globalBaseUrl: String? = null,
    val feedList: List<FeedDto>? = null
)

@JsonClass(generateAdapter = false)
enum class AppName { taz, lmd }

@JsonClass(generateAdapter = false)
enum class AppType { production, test, local }
