package de.taz.app.android.api.variables

import com.squareup.moshi.JsonClass
import de.taz.app.android.api.dto.Sorting
import de.taz.app.android.singletons.JsonHelper

@JsonClass(generateAdapter = true)
data class SearchVariables(
    val text: String? = null,
    val title: String? = null,
    val author: String? = null,
    val offset: Int? = null,
    val sorting: Sorting? = null,
    val pubDateFrom: String? = null,
    val pubDateUntil: String? = null,
): Variables {
    override fun toJson(): String = JsonHelper.toJson(this)
}