package de.taz.app.android.api.mappers

import de.taz.app.android.api.dto.SubscriptionInfoDto
import de.taz.app.android.api.models.SubscriptionInfo

object SubscriptionInfoMapper {
    fun from(subscriptionInfoDto: SubscriptionInfoDto): SubscriptionInfo {
        return SubscriptionInfo(
            SubscriptionStatusMapper.from(subscriptionInfoDto.status),
            subscriptionInfoDto.message,
            subscriptionInfoDto.token
        )

    }
}