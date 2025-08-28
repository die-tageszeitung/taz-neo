package de.taz.app.android.api.mappers

import de.taz.app.android.api.dto.SubscriptionFormDataErrorDto
import de.taz.app.android.api.dto.SubscriptionFormDataErrorDto.UNKNOWN
import de.taz.app.android.api.dto.SubscriptionFormDataErrorDto.invalidMail
import de.taz.app.android.api.dto.SubscriptionFormDataErrorDto.noCity
import de.taz.app.android.api.dto.SubscriptionFormDataErrorDto.noFirstName
import de.taz.app.android.api.dto.SubscriptionFormDataErrorDto.noMail
import de.taz.app.android.api.dto.SubscriptionFormDataErrorDto.noSurname
import de.taz.app.android.api.models.SubscriptionFormDataError

object SubscriptionFormDataErrorMapper {
    fun from(subscriptionFormDataErrorDto: SubscriptionFormDataErrorDto): SubscriptionFormDataError =
        when (subscriptionFormDataErrorDto) {
            noMail -> SubscriptionFormDataError.noMail
            invalidMail -> SubscriptionFormDataError.invalidMail
            noSurname -> SubscriptionFormDataError.noSurname
            noFirstName -> SubscriptionFormDataError.noFirstName
            noCity -> SubscriptionFormDataError.noCity
            UNKNOWN -> SubscriptionFormDataError.UNKNOWN_RESPONSE
        }

}
