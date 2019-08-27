package de.taz.app.android.persistence.typeconverters

import androidx.room.TypeConverter
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import de.taz.app.android.api.models.Frame

class FrameListTypeConverter {

    private val moshi = Moshi.Builder().build()
    private val stringListType = Types.newParameterizedType(List::class.java, Frame::class.java)
    private val adapter = moshi.adapter<List<Frame>>(stringListType)

    @TypeConverter
    fun toString(frameList: List<Frame>): String {
        return  adapter.toJson(frameList)
    }

    @TypeConverter
    fun toFrames(value: String): List<Frame> {
        return adapter.fromJson(value) ?: listOf()
    }

}