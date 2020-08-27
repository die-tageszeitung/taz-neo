package de.taz.app.android.api.variables

import com.squareup.moshi.JsonClass
import de.taz.app.android.api.dto.DeviceType
import de.taz.app.android.firebase.FirebaseHelper
import de.taz.app.android.singletons.AuthHelper
import de.taz.app.android.singletons.JsonHelper


@JsonClass(generateAdapter = true)
data class SubscriptionVariables(
    val tazId: String? = null,
    val idPassword: String? = null,
    val surname: String? = null,
    val firstName: String? = null,
    val street: String? = null,
    val city: String? = null,
    val postcode: String? = null,
    val country: String? = null,
    val phone: String? = null,
    val price: Int? = null,
    val iban: String? = null,
    val accountHolder: String? = null,
    val installationId: String = AuthHelper.getInstance().installationId,
    val pushToken: String? = FirebaseHelper.getInstance().firebaseToken,
    val deviceType: DeviceType = DeviceType.android
) : Variables {
    override fun toJson() = JsonHelper.toJson(this)
}