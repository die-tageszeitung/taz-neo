package de.taz.app.android.api.variables

import de.taz.app.android.BuildConfig
import kotlinx.serialization.Serializable

@Serializable
data class SubscriptionVariables(
    val installationId: String,
    val pushToken: String? = "",
    val tazId: String,
    val idPassword: String,
    val surname: String? = null,
    val firstName: String? = null,
    val nameAffix: String? = null,
    val street: String,
    val city: String,
    val postcode: String,
    val country: String,
    val phone: String? = null,
    val price: Int,
    val iban: String,
    val accountHolder: String? = null,
    val comment: String? = null,
    val deviceFormat: DeviceFormat,
    val deviceName: String? = android.os.Build.MODEL,
    val deviceVersion: String? = android.os.Build.VERSION.RELEASE,
    val appVersion: String = BuildConfig.VERSION_NAME,
    val deviceType: DeviceType = DeviceType.android,
    val deviceOS: String? = System.getProperty("os.version"),
) : Variables