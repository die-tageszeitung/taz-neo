package de.taz.app.android.api.dto

import de.taz.app.android.api.EnumSerializer
import kotlinx.serialization.Serializable

@Serializable(with = IssueStatusDtoEnumSerializer::class)
enum class IssueStatusDto {
    public,
    demo,
    regular,
    locked,
    UNKNOWN
}

object IssueStatusDtoEnumSerializer :
    EnumSerializer<IssueStatusDto>(IssueStatusDto.values(), IssueStatusDto.UNKNOWN)
