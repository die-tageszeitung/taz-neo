package de.taz.app.android.api.variables

import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import de.taz.app.android.singletons.JsonHelper

@JsonClass(generateAdapter = true)
data class CheckSubscriptionIdVariables(val subscriptionId: Int, val password: String) : Variables {
    override fun toJson() = JsonHelper.toJson(this)
}

