package de.taz.app.android.persistence.typeconverters

import androidx.room.TypeConverter
import de.taz.app.android.api.models.IssueStatus


class IssueStatusTypeConverter {
    @TypeConverter
    fun toString(issueStatus: IssueStatus): String {
        return issueStatus.name
    }

    @TypeConverter
    fun toIssueState(value: String): IssueStatus {
        return IssueStatus.valueOf(value)
    }
}