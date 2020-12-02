package de.taz.app.android.api.variables

import com.squareup.moshi.JsonClass
import de.taz.app.android.BuildConfig
import de.taz.app.android.api.dto.DeviceFormat
import de.taz.app.android.api.dto.DeviceType
import de.taz.app.android.firebase.FirebaseHelper
import de.taz.app.android.singletons.AuthHelper
import de.taz.app.android.singletons.JsonHelper


@JsonClass(generateAdapter = true)
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
    val deviceName: String? = android.os.Build.MODEL,
    val deviceVersion: String? = android.os.Build.VERSION.RELEASE,
    val appVersion: String = BuildConfig.VERSION_NAME,
    val deviceFormat: DeviceFormat = DeviceFormat.mobile,
    val deviceType: DeviceType = DeviceType.android,
    val deviceOS: String? = System.getProperty("os.version"),
) : Variables {
    override fun toJson() = JsonHelper.toJson(this)
}