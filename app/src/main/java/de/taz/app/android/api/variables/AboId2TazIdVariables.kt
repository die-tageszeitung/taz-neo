package de.taz.app.android.api.variables

import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi

@JsonClass(generateAdapter = true)
data class AboId2TazIdVariables(
    val tazId: Int,
    val idPw: String,
    val aboId: Int,
    val aboPw: String,
    val installationID: String,
    val deviceId: String? = null,
    val surname: String? = null,
    val firstname: String? = null
) : Variables {

    override fun toJson(): String {
        val moshi = Moshi.Builder().build()
        val adapter = moshi.adapter(AboId2TazIdVariables::class.java)

        return adapter.toJson(this)
    }

}
