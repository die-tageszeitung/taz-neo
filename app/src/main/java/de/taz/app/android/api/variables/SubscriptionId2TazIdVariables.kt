package de.taz.app.android.api.variables

import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import de.taz.app.android.api.dto.DeviceType
import de.taz.app.android.firebase.FirebaseHelper
import de.taz.app.android.singletons.AuthHelper

@JsonClass(generateAdapter = true)
data class SubscriptionId2TazIdVariables(
    val tazId: String,
    val idPassword: String,
    val subscriptionId: Int,
    val subscriptionPassword: String,
    val surname: String? = null,
    val firstName: String? = null,
    val installationId: String = AuthHelper.getInstance().installationId,
    val pushToken: String? = FirebaseHelper.getInstance().firebaseToken,
    val deviceType: DeviceType = DeviceType.android
) : Variables {

    override fun toJson(): String {
        val moshi = Moshi.Builder().build()
        val adapter = moshi.adapter(SubscriptionId2TazIdVariables::class.java)

        return adapter.toJson(this)
    }

}
