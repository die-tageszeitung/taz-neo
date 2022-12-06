package de.taz.app.android.api.variables

import kotlinx.serialization.Serializable

@Serializable
enum class SearchFilter {
    all,
    taz,
    LMd,
    Kontext,
    weekend
}