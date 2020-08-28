package de.taz.app.android.api.variables

import com.squareup.moshi.JsonClass
import de.taz.app.android.api.dto.DeviceType
import de.taz.app.android.firebase.FirebaseHelper
import de.taz.app.android.singletons.AuthHelper
import de.taz.app.android.singletons.JsonHelper


@JsonClass(generateAdapter = true)
data class SubscriptionVariables(
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
    val installationId: String = AuthHelper.getInstance().installationId,
    val pushToken: String? = FirebaseHelper.getInstance().firebaseToken,
    val deviceType: DeviceType = DeviceType.android
) : Variables {
    override fun toJson() = JsonHelper.toJson(this)
}