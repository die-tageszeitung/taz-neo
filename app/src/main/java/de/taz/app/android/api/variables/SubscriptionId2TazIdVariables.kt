package de.taz.app.android.api.variables

import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import de.taz.app.android.firebase.FirebaseHelper
import de.taz.app.android.util.AuthHelper

@JsonClass(generateAdapter = true)
data class SubscriptionId2TazIdVariables(
    val tazId: Int,
    val idPw: String,
    val aboId: Int,
    val aboPw: String,
    val surname: String? = null,
    val firstname: String? = null,
    val installationId: String = AuthHelper.getInstance().installationId,
    val deviceId: String? = FirebaseHelper.getInstance().firebaseToken
) : Variables {

    override fun toJson(): String {
        val moshi = Moshi.Builder().build()
        val adapter = moshi.adapter(SubscriptionId2TazIdVariables::class.java)

        return adapter.toJson(this)
    }

}
