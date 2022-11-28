package de.taz.app.android.api.variables

import de.taz.app.android.BuildConfig
import de.taz.app.android.api.models.Sorting
import kotlinx.serialization.Serializable

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
): Variables