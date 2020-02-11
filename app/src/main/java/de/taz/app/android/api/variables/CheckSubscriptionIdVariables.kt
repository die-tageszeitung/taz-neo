package de.taz.app.android.api.variables

import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi

@JsonClass(generateAdapter = true)
data class CheckSubscriptionIdVariables(val subscriptionId: Int, val password: String): Variables {

    override fun toJson(): String {
        val moshi = Moshi.Builder().build()
        val adapter = moshi.adapter(CheckSubscriptionIdVariables::class.java)

        return adapter.toJson(this)
    }

}
