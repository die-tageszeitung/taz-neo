package de.taz.app.android.api

import de.taz.app.android.api.variables.Variables

data class Query(
    private val query: String,
    var variables: Variables? = null
) {

    fun toJson(): String {
        return "{\"query\":\"$query\", \"variables\":${variables?.toJson() ?: "{}"}}"
    }

}