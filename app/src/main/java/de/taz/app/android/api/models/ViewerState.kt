package de.taz.app.android.api.models

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Ignore
import androidx.room.PrimaryKey
import de.taz.app.android.api.dto.SectionType
import de.taz.app.android.api.interfaces.SectionOperations
import de.taz.app.android.persistence.repository.IssueKey
import java.util.*

@Entity(
    tableName = "ViewerState",
    primaryKeys = ["displayableName"],
)
data class ViewerState(
    val displayableName: String,
    val scrollPosition: Int
)
