package de.taz.app.android.persistence.typeconverters

import androidx.room.TypeConverter
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import de.taz.app.android.api.models.Frame
import de.taz.app.android.singletons.JsonHelper

class FrameListTypeConverter {

    private val stringListType = Types.newParameterizedType(List::class.java, Frame::class.java)
    private val adapter = JsonHelper.moshi.adapter<List<Frame>>(stringListType)

    @TypeConverter
    fun toString(frameList: List<Frame>?): String {
        return  adapter.toJson(frameList)
    }

    @TypeConverter
    fun toFrames(value: String): List<Frame>? {
        return adapter.fromJson(value)
    }

}