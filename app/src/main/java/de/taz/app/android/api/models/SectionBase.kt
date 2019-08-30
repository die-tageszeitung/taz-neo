package de.taz.app.android.api.models

import androidx.room.Entity

@Entity(tableName = "Section")
data class SectionBase (
    val sectionFileName: String,
    val title: String,
    val type: SectionType
) {
    constructor(section: Section) : this(section.sectionHtml.name, section.title, section.type)
}

