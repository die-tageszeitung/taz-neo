package de.taz.app.android.api.mappers

import android.util.Log
import de.taz.app.android.api.dto.IssueStatusDto
import de.taz.app.android.api.dto.IssueStatusDto.UNKNOWN
import de.taz.app.android.api.dto.IssueStatusDto.demo
import de.taz.app.android.api.dto.IssueStatusDto.locked
import de.taz.app.android.api.dto.IssueStatusDto.public
import de.taz.app.android.api.dto.IssueStatusDto.regular
import de.taz.app.android.api.models.IssueStatus

object IssueStatusMapper {
    fun from(issueStatusDto: IssueStatusDto): IssueStatus = when (issueStatusDto) {
        public -> IssueStatus.public
        demo -> IssueStatus.demo
        regular -> IssueStatus.regular
        locked -> IssueStatus.locked
        UNKNOWN -> {
            val hint = "Encountered UNKNOWN IssueStatusDto, falling back to IssueStatus.public"
            Log.w(CustomerTypeMapper::class.java.name, hint)
            IssueStatus.public
        }
    }
}