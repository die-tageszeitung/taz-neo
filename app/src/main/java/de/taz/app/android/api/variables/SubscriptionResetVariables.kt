package de.taz.app.android.api.variables

import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi

@JsonClass(generateAdapter = true)
data class SubscriptionResetVariables(
    val subscriptionId: Int
): Variables {

    override fun toJson(): String {
        val moshi = Moshi.Builder().build()
        val adapter = moshi.adapter(SubscriptionResetVariables::class.java)

        return adapter.toJson(this)
    }
}
