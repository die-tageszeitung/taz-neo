package de.taz.app.android.api.variables

import de.taz.app.android.BuildConfig
import kotlinx.serialization.Serializable

@Serializable
data class TrialSubscriptionVariables(
    val tazId: String,
    val idPassword: String,
    val installationId: String,
    val pushToken: String?,
    val surname: String? = null,
    val firstName: String? = null,
    val nameAffix: String? = null,
    val deviceFormat: DeviceFormat,
    val deviceName: String? = android.os.Build.MODEL,
    val deviceVersion: String? = android.os.Build.VERSION.RELEASE,
    val appVersion: String = BuildConfig.VERSION_NAME,
    val appVersionCode: Int = BuildConfig.VERSION_CODE,
    val deviceType: DeviceType = DeviceType.android,
    val deviceOS: String? = System.getProperty("os.version"),
) : Variables