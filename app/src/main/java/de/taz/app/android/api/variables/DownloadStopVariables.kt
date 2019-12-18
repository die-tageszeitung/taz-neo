package de.taz.app.android.api.variables

import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi

@JsonClass(generateAdapter = true)
data class DownloadStopVariables(
    val id: String,
    val time: Float
): Variables {

    override fun toJson(): String {
        val moshi = Moshi.Builder().build()
        val adapter = moshi.adapter(DownloadStopVariables::class.java)

        return adapter.toJson(this)
    }
}