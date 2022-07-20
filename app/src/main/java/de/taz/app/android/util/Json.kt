package de.taz.app.android.util

import kotlinx.serialization.json.Json

val Json = Json {
    ignoreUnknownKeys = true
}