package de.taz.android

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

// Run these tests with `./gradlew test -p buildSrc`

class VersionHelperTest {
    @Test
    fun `taz release tag is parsed and padded correctly`() {
        val versionHelper = VersionHelper
        val gitTag = "1.6.1"

        val result = versionHelper.gitTagToVersionCode(gitTag)

        assertEquals(10601900, result)
    }

    @Test
    fun `taz pre-release alpha tag is parsed and padded correctly`() {
        val versionHelper = VersionHelper
        val gitTag = "1.6.1-alpha.2"

        val result = versionHelper.gitTagToVersionCode(gitTag)

        assertEquals(10601102, result)
    }

    @Test
    fun `taz pre-release beta tag is parsed and padded correctly`() {
        val versionHelper = VersionHelper
        val gitTag = "1.6.1-beta.3"

        val result = versionHelper.gitTagToVersionCode(gitTag)

        assertEquals(10601203, result)
    }


    @Test
    fun `taz pre-release rc tag is parsed and padded correctly`() {
        val versionHelper = VersionHelper
        val gitTag = "1.6.1-rc.10"

        val result = versionHelper.gitTagToVersionCode(gitTag)

        assertEquals(10601310, result)
    }

    @Test
    fun `taz unknown pre-release type tag is not allowed`() {
        val versionHelper = VersionHelper
        val gitTag = "1.6.1-gamma.1"

        assertThrows(IllegalStateException::class.java) {
           versionHelper.gitTagToVersionCode(gitTag)
        }
    }

    @Test
    fun `taz debug tag is parsed and padded correctly`() {
        val versionHelper = VersionHelper
        val gitTag = "1.6.1-187-gffc6a8321"

        val result = versionHelper.gitTagToVersionCode(gitTag)

        assertEquals(10601900, result)
    }

    @Test
    fun `taz debug dirty tag is parsed and padded correctly`() {
        val versionHelper = VersionHelper
        val gitTag = "1.6.1-187-gffc6a8321-dirty"

        val result = versionHelper.gitTagToVersionCode(gitTag)

        assertEquals(10601900, result)
    }



    // region: exceeding allowed digit tests
    @Test
    fun `taz minor version must not exceed defined digits`() {
        val versionHelper = VersionHelper
        val gitTag = "1.600.1"

        assertThrows(IllegalStateException::class.java) {
            versionHelper.gitTagToVersionCode(gitTag)
        }
    }

    @Test
    fun `taz patch version must not exceed defined digits`() {
        val versionHelper = VersionHelper
        val gitTag = "1.6.100"

        assertThrows(IllegalStateException::class.java) {
            versionHelper.gitTagToVersionCode(gitTag)
        }
    }

    @Test
    fun `taz pre release version must not exceed defined digits`() {
        val versionHelper = VersionHelper
        val gitTag = "1.6.1-rc.100"

        assertThrows(IllegalStateException::class.java) {
            versionHelper.gitTagToVersionCode(gitTag)
        }
    }
    // endregion
}