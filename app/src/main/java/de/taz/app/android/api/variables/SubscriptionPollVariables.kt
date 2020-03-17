package de.taz.app.android.api.variables

import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import de.taz.app.android.singletons.AuthHelper
import de.taz.app.android.singletons.JsonHelper

@JsonClass(generateAdapter = true)
data class SubscriptionPollVariables(
    val installationId: String = AuthHelper.getInstance().installationId
) : Variables {
    override fun toJson() = JsonHelper.toJson(this)
}
