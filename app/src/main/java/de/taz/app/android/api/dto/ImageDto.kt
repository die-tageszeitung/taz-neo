package de.taz.app.android.api.dto

import kotlinx.serialization.Serializable
import de.taz.app.android.api.models.ImageResolution
import de.taz.app.android.api.models.ImageType
import de.taz.app.android.singletons.Storable

@Serializable
data class ImageDto(
    override val name: String,
    override val storageType: StorageType,
    val moTime: Long,
    val sha256: String,
    val size: Long,
    val type: ImageType,
    val alpha: Float,
    val resolution: ImageResolution
): Storable
