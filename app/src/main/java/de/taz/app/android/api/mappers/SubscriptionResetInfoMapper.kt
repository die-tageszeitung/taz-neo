package de.taz.app.android.api.mappers

import de.taz.app.android.api.dto.SubscriptionResetInfoDto
import de.taz.app.android.api.models.SubscriptionResetInfo

object SubscriptionResetInfoMapper {
    fun from(subscriptionResetInfoDto: SubscriptionResetInfoDto): SubscriptionResetInfo {
        return SubscriptionResetInfo(
            SubscriptionResetStatusMapper.from(subscriptionResetInfoDto.status)
        )
    }
}