package de.taz.app.android.api.variables

import com.squareup.moshi.JsonClass
import de.taz.app.android.BuildConfig
import de.taz.app.android.api.dto.DeviceFormat
import de.taz.app.android.api.dto.DeviceType
import de.taz.app.android.singletons.JsonHelper

@JsonClass(generateAdapter = true)
data class DownloadStartVariables(
    val feedName: String,
    val issueDate: String,
    val isAutomatically: Boolean,
    val installationId: String,
    val isPush: Boolean,
    val deviceName: String = android.os.Build.MODEL,
    val deviceVersion: String = android.os.Build.VERSION.RELEASE,
    val appVersion: String = BuildConfig.VERSION_NAME,
    val deviceFormat: DeviceFormat = DeviceFormat.mobile,
    val deviceType: DeviceType = DeviceType.android,
    val deviceOS: String? = System.getProperty("os.version"),
    val pushToken: String?,
    val deviceMessageSound: String? = null,
    val textNotification: Boolean = true
) : Variables {
    override fun toJson() = JsonHelper.toJson(this)
}
