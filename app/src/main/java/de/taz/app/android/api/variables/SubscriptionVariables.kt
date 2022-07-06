package de.taz.app.android.api.variables

import kotlinx.serialization.Serializable
import de.taz.app.android.BuildConfig
import de.taz.app.android.api.dto.DeviceFormat
import de.taz.app.android.api.dto.DeviceType
import kotlinx.serialization.Required
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json


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
    @Required  val deviceName: String? = android.os.Build.MODEL,
    @Required val deviceVersion: String? = android.os.Build.VERSION.RELEASE,
    @Required val appVersion: String = BuildConfig.VERSION_NAME,
    @Required val deviceType: DeviceType = DeviceType.android,
    @Required val deviceOS: String? = System.getProperty("os.version"),
) : Variables {
    override fun toJson() = Json.encodeToString(this)
}