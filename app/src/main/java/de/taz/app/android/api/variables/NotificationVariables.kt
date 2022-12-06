package de.taz.app.android.api.variables

import de.taz.app.android.BuildConfig
import kotlinx.serialization.Serializable

@Serializable
data class NotificationVariables(
    val pushToken: String,
    val deviceFormat: DeviceFormat,
    val oldToken: String? = null,
    val deviceMessageSound: String? = null,
    val textNotification: Boolean = true,
    val deviceName: String? = android.os.Build.MODEL,
    val deviceVersion: String? = android.os.Build.VERSION.RELEASE,
    val appVersion: String = BuildConfig.VERSION_NAME,
    val deviceType: DeviceType = DeviceType.android,
    val deviceOS: String? = System.getProperty("os.version"),
): Variables