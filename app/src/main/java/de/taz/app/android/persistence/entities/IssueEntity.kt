package de.taz.app.android.persistence.entities

import androidx.room.Entity
import androidx.room.Ignore
import androidx.room.TypeConverter
import de.taz.app.android.api.models.*

@Entity(tableName = "Issue",
    primaryKeys = ["date" ]
) //TODO ADD FEEDNAME
class IssueEntity(
    val date: String,
    val key: String? = null,
    val baseUrl: String,
    val status: IssueStatus,
    val minResourceVersion: Int,
    val zipName: String? = null,
    val zipPdfName: String? = null,
    val navButton: NavButton? = null,
    val imprint: Article,
    val fileList: List<String>,
    val fileListPdf: List<String>,
    @Ignore val sectionList: List<Section>? = null,
    @Ignore val pageList: List<Page>? = null
)


class IssueStatusConverter {
    @TypeConverter
    fun toString(issueStatus: IssueStatus): String {
        return issueStatus.name
    }

    @TypeConverter
    fun toIssueState(value: String): IssueStatus {
        return IssueStatus.valueOf(value)
    }
}

//TODO
class NavButtonConverter {
    @TypeConverter
    fun toString(navButton: NavButton?): String {
        return ""
    }
}