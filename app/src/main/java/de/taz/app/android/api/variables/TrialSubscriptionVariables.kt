package de.taz.app.android.api.variables

import kotlinx.serialization.Serializable
import de.taz.app.android.BuildConfig
import de.taz.app.android.api.dto.DeviceFormat
import de.taz.app.android.api.dto.DeviceType
import kotlinx.serialization.Required
import kotlinx.serialization.encodeToString
import de.taz.app.android.util.Json


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
    @Required val deviceName: String? = android.os.Build.MODEL,
    @Required val deviceVersion: String? = android.os.Build.VERSION.RELEASE,
    @Required val appVersion: String = BuildConfig.VERSION_NAME,
    @Required val deviceType: DeviceType = DeviceType.android,
    @Required val deviceOS: String? = System.getProperty("os.version"),
) : Variables