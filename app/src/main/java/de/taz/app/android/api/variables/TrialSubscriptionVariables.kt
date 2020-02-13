package de.taz.app.android.api.variables

import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import de.taz.app.android.firebase.FirebaseHelper
import de.taz.app.android.singletons.AuthHelper


@JsonClass(generateAdapter = true)
data class TrialSubscriptionVariables(
    val tazId: String,
    val idPassword: String,
    val surname: String? = null,
    val firstName: String? = null,
    val installationId: String = AuthHelper.getInstance().installationId,
    val deviceId: String? = FirebaseHelper.getInstance().firebaseToken
) : Variables {

    override fun toJson(): String {
        val moshi = Moshi.Builder().build()
        return moshi.adapter(TrialSubscriptionVariables::class.java).toJson(this)
    }

}