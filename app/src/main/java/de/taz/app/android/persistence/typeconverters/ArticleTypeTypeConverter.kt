package de.taz.app.android.persistence.typeconverters

import androidx.room.TypeConverter
import de.taz.app.android.api.models.ArticleType


class ArticleTypeTypeConverter {
    @TypeConverter
    fun toString(appType: ArticleType): String {
        return appType.name
    }
    @TypeConverter
    fun toAppTypeEnum(value: String): ArticleType{
        return ArticleType.valueOf(value)
    }
}
