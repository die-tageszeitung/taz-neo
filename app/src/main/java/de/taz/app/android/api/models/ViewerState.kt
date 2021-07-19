package de.taz.app.android.api.models

import androidx.room.Entity

@Entity(
    tableName = "ViewerState",
    primaryKeys = ["displayableName"],
)
data class ViewerState(
    val displayableName: String,
    val scrollPosition: Int
)
