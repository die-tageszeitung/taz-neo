package de.taz.app.android.api.mappers

import de.taz.app.android.api.dto.CycleDto
import de.taz.app.android.api.dto.CycleDto.*
import de.taz.app.android.api.models.Cycle

object CycleMapper {
    fun from(cycleDto: CycleDto): Cycle {
        return when (cycleDto) {
            daily -> Cycle.daily
            weekly -> Cycle.weekly
            monthly -> Cycle.monthly
            quarterly -> Cycle.quarterly
            yearly -> Cycle.yearly
            UNKNOWN -> throw UnknownEnumValueException("Can not map UNKNOWN CycleDto to a Cycle")
        }
    }
}

