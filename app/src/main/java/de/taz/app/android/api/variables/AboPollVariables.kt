package de.taz.app.android.api.variables

import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import de.taz.app.android.util.AuthHelper

@JsonClass(generateAdapter = true)
data class AboPollVariables(
    val installationId: String = AuthHelper.getInstance().installationId
) : Variables {

    override fun toJson(): String {
        val moshi = Moshi.Builder().build()
        val adapter = moshi.adapter(AboPollVariables::class.java)

        return adapter.toJson(this)
    }

}