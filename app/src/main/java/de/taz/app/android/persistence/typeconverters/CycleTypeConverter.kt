package de.taz.app.android.persistence.typeconverters

import androidx.room.TypeConverter
import de.taz.app.android.api.dto.Cycle


class CycleTypeConverter{
    @TypeConverter
    fun toString(cycle: Cycle): String {
        return cycle.name
    }
    @TypeConverter
    fun toAppTypeEnum(value: String): Cycle {
        return Cycle.valueOf(value)
    }
}