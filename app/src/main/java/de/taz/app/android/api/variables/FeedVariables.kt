package de.taz.app.android.api.variables;

import com.squareup.moshi.JsonClass;
import de.taz.app.android.singletons.JsonHelper

@JsonClass(generateAdapter = true)
data class FeedVariables(
        val feedName: String? = null
) : Variables {
    override fun toJson(): String = JsonHelper.toJson(this)
}
