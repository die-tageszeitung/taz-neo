package de.taz.app.android.api.variables

import com.squareup.moshi.JsonClass
import de.taz.app.android.singletons.JsonHelper

@JsonClass(generateAdapter = true)
data class DownloadStopVariables(
    val id: String,
    val time: Float
): Variables {
    override fun toJson() = JsonHelper.toJson(this)
}
