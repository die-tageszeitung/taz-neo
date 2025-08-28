package de.taz.app.android.api.mappers

import de.taz.app.android.api.dto.SubscriptionResetStatusDto
import de.taz.app.android.api.dto.SubscriptionResetStatusDto.UNKNOWN
import de.taz.app.android.api.dto.SubscriptionResetStatusDto.invalidConnection
import de.taz.app.android.api.dto.SubscriptionResetStatusDto.invalidSubscriptionId
import de.taz.app.android.api.dto.SubscriptionResetStatusDto.noMail
import de.taz.app.android.api.dto.SubscriptionResetStatusDto.ok
import de.taz.app.android.api.models.SubscriptionResetStatus

object SubscriptionResetStatusMapper {
    fun from(statusDto: SubscriptionResetStatusDto): SubscriptionResetStatus = when(statusDto) {
        ok -> SubscriptionResetStatus.ok
        invalidSubscriptionId -> SubscriptionResetStatus.invalidSubscriptionId
        noMail -> SubscriptionResetStatus.noMail
        invalidConnection -> SubscriptionResetStatus.invalidConnection
        UNKNOWN -> SubscriptionResetStatus.UNKNOWN_RESPONSE
    }

}
