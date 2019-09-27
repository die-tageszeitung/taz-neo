package de.taz.app.android.api.variables

import com.squareup.moshi.Moshi

data class AuthenticationVariables(val user: String, val password: String): Variables {

    override fun toJson(): String {
        val moshi = Moshi.Builder().build()
        val adapter = moshi.adapter(AuthenticationVariables::class.java)

        return adapter.toJson(this)
    }
}
