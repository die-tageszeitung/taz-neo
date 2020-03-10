package de.taz.app.android.api.variables

import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import de.taz.app.android.singletons.JsonHelper

@JsonClass(generateAdapter = true)
data class IssueVariables(
    val feedName: String? = null,
    val issueDate: String? = null,
    val limit: Int = 1
): Variables {
    override fun toJson(): String = JsonHelper.toJson(this)
}
