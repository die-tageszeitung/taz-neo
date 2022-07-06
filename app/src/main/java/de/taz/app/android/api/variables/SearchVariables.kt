package de.taz.app.android.api.variables

import kotlinx.serialization.Serializable
import de.taz.app.android.BuildConfig
import de.taz.app.android.api.dto.DeviceFormat
import de.taz.app.android.api.dto.DeviceType
import de.taz.app.android.api.dto.SearchFilter
import de.taz.app.android.api.dto.Sorting
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable
data class SearchVariables(
    val text: String? = null,
    val title: String? = null,
    val author: String? = null,
    val sessionId: String? = null,
    val offset: Int? = null,
    val rowCnt: Int? = null,
    val sorting: Sorting? = null,
    val searchTime: String? = null,
    val filter: SearchFilter? = SearchFilter.all,
    val pubDateFrom: String? = null,
    val pubDateUntil: String? = null,
    val deviceName: String? = android.os.Build.MODEL,
    val deviceVersion: String? = android.os.Build.VERSION.RELEASE,
    val appVersion: String = BuildConfig.VERSION_NAME,
    val deviceFormat: DeviceFormat,
    val deviceType: DeviceType = DeviceType.android,
    val deviceOS: String? = System.getProperty("os.version")
): Variables {
    override fun toJson(): String = Json.encodeToString(this)
}