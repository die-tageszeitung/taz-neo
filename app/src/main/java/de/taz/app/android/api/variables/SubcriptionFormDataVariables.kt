package de.taz.app.android.api.variables

import de.taz.app.android.BuildConfig
import de.taz.app.android.api.dto.DeviceFormat
import de.taz.app.android.api.dto.DeviceType
import de.taz.app.android.api.dto.SubscriptionFormDataType
import kotlinx.serialization.Serializable


@Serializable
data class SubscriptionFormDataVariables(
    val subscriptionFormDataType: SubscriptionFormDataType,
    val mail: String?,
    val surname: String?,
    val firstname: String?,
    val street: String?,
    val city: String?,
    val postcode: String?,
    val country: String?,
    val message: String?,
    val requestCurrentSubscriptionOpportunities: Boolean?,
    val deviceName: String? = android.os.Build.MODEL,
    val deviceVersion: String? = android.os.Build.VERSION.RELEASE,
    val appVersion: String = BuildConfig.VERSION_NAME,
    val deviceFormat: DeviceFormat,
    val deviceType: DeviceType = DeviceType.android,
    val deviceOS: String? = System.getProperty("os.version"),
) : Variables