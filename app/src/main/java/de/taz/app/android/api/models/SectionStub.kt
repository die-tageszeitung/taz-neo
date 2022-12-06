package de.taz.app.android.api.models

import androidx.room.Entity
import androidx.room.Ignore
import androidx.room.PrimaryKey
import de.taz.app.android.api.interfaces.SectionOperations
import java.util.*

@Entity(tableName = "Section")
data class SectionStub(
    @PrimaryKey val sectionFileName: String,
    val issueDate: String,
    override val title: String,
    val type: SectionType,
    override val extendedTitle: String?,
    val dateDownload: Date?
) : SectionOperations {

    @Ignore
    override val key: String = sectionFileName

    constructor(section: Section) : this(
        section.sectionHtml.name,
        section.issueDate,
        section.title,
        section.type,
        section.extendedTitle,
        section.dateDownload
    )
}

