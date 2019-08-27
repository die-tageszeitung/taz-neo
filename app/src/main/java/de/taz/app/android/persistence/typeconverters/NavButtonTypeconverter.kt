package de.taz.app.android.persistence.typeconverters

import androidx.room.TypeConverter
import de.taz.app.android.api.models.NavButton
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonConfiguration

class NavButtonTypeconverter {

    private val json = Json(JsonConfiguration.Stable)

    @TypeConverter
    fun toString(navButton: NavButton): String {
        return json.stringify(NavButton.serializer(), navButton)
    }

    @TypeConverter
    fun toNavButton(value: String): NavButton {
        return json.parse(NavButton.serializer(), value)
    }

}