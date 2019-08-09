package de.taz.app.android.api

data class Query(
    private val query: String,
    private val variables: Map<String, String> = mapOf()
) {

    fun setVariables(variables: Map<String, String>): Query {
        return this.copy(query = query, variables = variables)
    }

    fun toJson(): String {
        return "{\"query\":\"$query\", \"variables\":${variablesToString()}}"
    }

    private fun variablesToString(): String {
        val variableString = variables.keys.joinToString(
            separator = ",",
            transform = { key -> "\"$key\":\"${variables[key]}\""}
        )
        return "{$variableString}"
    }

}