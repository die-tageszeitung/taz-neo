package de.taz.app.android.api.mappers

import de.taz.app.android.api.dto.SubscriptionFormDataDto
import de.taz.app.android.api.models.SubscriptionFormData

object SubscriptionFormDataMapper {
    fun from(subscriptionFormDataDto: SubscriptionFormDataDto): SubscriptionFormData {
        return SubscriptionFormData(
            subscriptionFormDataDto.error?.let { SubscriptionFormDataErrorMapper.from(it) },
            subscriptionFormDataDto.errorMessage
        )
    }
}