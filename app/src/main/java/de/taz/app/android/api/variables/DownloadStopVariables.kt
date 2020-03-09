package de.taz.app.android.api.variables

import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import de.taz.app.android.singletons.JsonHelper

@JsonClass(generateAdapter = true)
data class DownloadStopVariables(
    val id: String,
    val time: Float
): Variables {
    override fun toJson() = JsonHelper.toJson(this)
}
