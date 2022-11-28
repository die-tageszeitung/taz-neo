package de.taz.app.android.api.variables

import de.taz.app.android.BuildConfig
import kotlinx.serialization.Serializable

@Serializable
data class DownloadStartVariables(
    val feedName: String,
    val issueDate: String,
    val isAutomatically: Boolean,
    val installationId: String,
    val isPush: Boolean,
    val deviceFormat: DeviceFormat,
    val deviceName: String = android.os.Build.MODEL,
    val deviceVersion: String = android.os.Build.VERSION.RELEASE,
    val appVersion: String = BuildConfig.VERSION_NAME,
    val deviceType: DeviceType = DeviceType.android,
    val deviceOS: String? = System.getProperty("os.version"),
    val pushToken: String?,
    val deviceMessageSound: String? = null,
    val textNotification: Boolean = true
) : Variables