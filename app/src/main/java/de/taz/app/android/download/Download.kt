package de.taz.app.android.download

import de.taz.app.android.api.models.File
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonConfiguration

@Serializable
class Download(
    val baseUrl: String,
    val folder: String,
    val name: String,
    val sha256: String? = null,
    val size: Int? = null
){
    constructor(baseUrl: String, folder: String, file: File) : this(baseUrl, folder, file.name, file.sha256, file.size)

    val url
        get() = "$baseUrl/$name"

    val path
        get() = "$folder/$name"

    fun serialize(): String {
        return json.stringify(serializer(), this)
    }

    companion object {
        private val json by lazy { Json(JsonConfiguration.Stable) }

        fun deserialize(serializedDownload: String): Download {
            return json.parse(serializer(), serializedDownload)
        }
    }

}