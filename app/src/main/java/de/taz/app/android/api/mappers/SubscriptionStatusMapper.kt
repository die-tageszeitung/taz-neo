package de.taz.app.android.api.mappers

import de.taz.app.android.api.dto.SubscriptionStatusDto
import de.taz.app.android.api.dto.SubscriptionStatusDto.UNKNOWN
import de.taz.app.android.api.dto.SubscriptionStatusDto.alreadyLinked
import de.taz.app.android.api.dto.SubscriptionStatusDto.elapsed
import de.taz.app.android.api.dto.SubscriptionStatusDto.ibanInvalidChecksum
import de.taz.app.android.api.dto.SubscriptionStatusDto.ibanNoIban
import de.taz.app.android.api.dto.SubscriptionStatusDto.ibanNoSepaCountry
import de.taz.app.android.api.dto.SubscriptionStatusDto.invalidAccountHolder
import de.taz.app.android.api.dto.SubscriptionStatusDto.invalidCity
import de.taz.app.android.api.dto.SubscriptionStatusDto.invalidConnection
import de.taz.app.android.api.dto.SubscriptionStatusDto.invalidCountry
import de.taz.app.android.api.dto.SubscriptionStatusDto.invalidFirstName
import de.taz.app.android.api.dto.SubscriptionStatusDto.invalidMail
import de.taz.app.android.api.dto.SubscriptionStatusDto.invalidPostcode
import de.taz.app.android.api.dto.SubscriptionStatusDto.invalidStreet
import de.taz.app.android.api.dto.SubscriptionStatusDto.invalidSurname
import de.taz.app.android.api.dto.SubscriptionStatusDto.nameTooLong
import de.taz.app.android.api.dto.SubscriptionStatusDto.noFirstName
import de.taz.app.android.api.dto.SubscriptionStatusDto.noPollEntry
import de.taz.app.android.api.dto.SubscriptionStatusDto.noSurname
import de.taz.app.android.api.dto.SubscriptionStatusDto.priceNotValid
import de.taz.app.android.api.dto.SubscriptionStatusDto.subscriptionIdNotValid
import de.taz.app.android.api.dto.SubscriptionStatusDto.tazIdNotValid
import de.taz.app.android.api.dto.SubscriptionStatusDto.tooManyPollTries
import de.taz.app.android.api.dto.SubscriptionStatusDto.valid
import de.taz.app.android.api.dto.SubscriptionStatusDto.waitForMail
import de.taz.app.android.api.dto.SubscriptionStatusDto.waitForProc
import de.taz.app.android.api.models.SubscriptionStatus

object SubscriptionStatusMapper {
    fun from(subscriptionStatusDto: SubscriptionStatusDto): SubscriptionStatus  = when(subscriptionStatusDto) {
        valid -> SubscriptionStatus.valid
        subscriptionIdNotValid -> SubscriptionStatus.subscriptionIdNotValid
        elapsed -> SubscriptionStatus.elapsed
        invalidConnection -> SubscriptionStatus.invalidConnection
        alreadyLinked -> SubscriptionStatus.alreadyLinked
        waitForMail -> SubscriptionStatus.waitForMail
        waitForProc -> SubscriptionStatus.waitForProc
        invalidMail -> SubscriptionStatus.invalidMail
        noSurname -> SubscriptionStatus.noSurname
        invalidSurname -> SubscriptionStatus.invalidSurname
        noFirstName -> SubscriptionStatus.noFirstName
        invalidFirstName -> SubscriptionStatus.invalidFirstName
        nameTooLong -> SubscriptionStatus.nameTooLong
        invalidAccountHolder -> SubscriptionStatus.invalidAccountHolder
        invalidStreet -> SubscriptionStatus.invalidStreet
        invalidCity -> SubscriptionStatus.invalidCity
        invalidPostcode -> SubscriptionStatus.invalidPostcode
        invalidCountry -> SubscriptionStatus.invalidCountry
        priceNotValid -> SubscriptionStatus.priceNotValid
        ibanInvalidChecksum -> SubscriptionStatus.ibanInvalidChecksum
        ibanNoSepaCountry -> SubscriptionStatus.ibanNoSepaCountry
        tooManyPollTries -> SubscriptionStatus.tooManyPollTries

        // FIXME(johannes): The following values are not documented and might not be used at all
        // https://redmine.hal.taz.de/projects/androidapq/wiki/App-Datenschnittstelle_f%C3%BCr_KundInen-Daten#Typ-SubscriptionStatus
        ibanNoIban -> SubscriptionStatus.ibanNoIban
        noPollEntry -> SubscriptionStatus.noPollEntry
        tazIdNotValid -> SubscriptionStatus.tazIdNotValid

        UNKNOWN -> SubscriptionStatus.UNKNOWN_RESPONSE
    }
}
