package de.taz.app.android.api.models

import androidx.room.Entity
import androidx.room.PrimaryKey
import de.taz.app.android.api.dto.SectionType
import de.taz.app.android.api.interfaces.SectionOperations

@Entity(tableName = "Section")
data class SectionBase (
    @PrimaryKey override val sectionFileName: String,
    val title: String,
    val type: SectionType
): SectionOperations {
    constructor(section: Section) : this(section.sectionHtml.name, section.title, section.type)
}

