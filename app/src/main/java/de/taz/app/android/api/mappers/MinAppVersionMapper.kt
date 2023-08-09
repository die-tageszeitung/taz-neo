package de.taz.app.android.api.mappers

import com.vdurmont.semver4j.Semver
import com.vdurmont.semver4j.SemverException
import de.taz.app.android.api.dto.AppDto
import de.taz.app.android.util.Log

object MinAppVersionMapper {
    fun from(appDto: AppDto): Semver? {
        val minVersionString = appDto.minVersion
            ?: return null

        return try {
            Semver(minVersionString, Semver.SemverType.LOOSE)
        } catch (e: SemverException) {
            Log(this::class.java.name).warn("Could not get semversion for minVersion: $minVersionString")
            null
        }
    }
}