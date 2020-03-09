package de.taz.app.android.api.variables

import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import de.taz.app.android.singletons.JsonHelper

@JsonClass(generateAdapter = true)
data class AuthenticationVariables(val user: String, val password: String): Variables {
    override fun toJson(): String = JsonHelper.toJson(this)
}
