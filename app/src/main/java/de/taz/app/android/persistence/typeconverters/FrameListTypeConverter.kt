package de.taz.app.android.persistence.typeconverters

import androidx.room.TypeConverter
import de.taz.app.android.api.models.Frame
import de.taz.app.android.util.Json
import kotlinx.serialization.encodeToString

class FrameListTypeConverter {

    @TypeConverter
    fun toString(frameList: List<Frame>?): String {
        return Json.encodeToString(frameList)
    }

    @TypeConverter
    fun toFrames(value: String): List<Frame>? {
        return Json.decodeFromString(value)
    }

}