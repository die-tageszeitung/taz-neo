package de.taz.android

import java.io.BufferedReader
import java.io.InputStreamReader


/**
 * The version code is created from the output of `git describe --tags`
 * - If the HEAD points directly to a tag it will be "1.6.1"
 * - If we are on some release candidate we will have tags like "1.6.1-rc.1"
 * - If not, then it will be something like "1.6.1-187-gffc6a8321",
 *   where "187" is the number of commits since the tag "1.6.1" and
 *   "gffc6a8321" the latest commit prefixed with "g" for git.
 * - If there are some uncommitted changes it might also be
 *   "1.6.1-187-gffc6a8321-dirty"
 *
 *   The pattern of the version code will look something like this:
 *   "MMmmPPTtt" where
 *      M - major version
 *      m - minor version
 *      P - patch version
 *      T - Type of pre-release
 *      t - Version of pre-release
 *      (Be aware since it's an int leading zeros are removed)
 */
object VersionHelper {

    private const val RELEASE_VERSION_PATTERN =
        """(\d+)\.(\d+)\.(\d+)"""

    private const val PRE_RELEASE_VERSION_PATTERN =
        """(\d+)\.(\d+)\.(\d+)-(rc|alpha|beta)\.(\d+)"""

    private const val DEVELOP_RELEASE_VERSION_PATTERN =
        """(\d+)\.(\d+)\.(\d+)(-(rc|alpha|beta)\.(\d+))?((-\d+-g[0-9a-fA-F]+(-dirty)?)|(-dirty))"""

    private const val MINOR_DIGITS = 2
    private const val PATCH_DIGITS = 2
    private const val PRE_RELEASE_TYPE_DIGITS = 1
    private const val PRE_RELEASE_VERSION_DIGITS = 2

    private const val PRE_RELEASE_TYPE_ALPHA = 1
    private const val PRE_RELEASE_TYPE_BETA = 2
    private const val PRE_RELEASE_TYPE_RC = 3
    private const val PRE_RELEASE_TYPE_RELEASE = 9
    private const val PRE_RELEASE_TYPE_DEVELOP = 9

    private val releaseRegex = RELEASE_VERSION_PATTERN.toRegex()
    private val preReleaseRegex = PRE_RELEASE_VERSION_PATTERN.toRegex()
    private val developReleaseRegex = DEVELOP_RELEASE_VERSION_PATTERN.toRegex()


    fun getTazVersionCode(): Int {
        val ignoreDirty = shouldIgnoreDirty()
        val gitTag = getTazGitTag(ignoreDirty)
        return gitTagToVersionCode(gitTag)
    }

    fun getTazVersionName(): String {
        val ignoreDirty = shouldIgnoreDirty()
        return getTazGitTag(ignoreDirty)
    }

    fun getLmdVersionCode(): Int {
        val ignoreDirty = shouldIgnoreDirty()
        val gitTag = getLmdGitTag(ignoreDirty)
        val versionTag = trimLmdPrefix(gitTag)
        return gitTagToVersionCode(versionTag)
    }

    fun getLmdVersionName(): String {
        val ignoreDirty = shouldIgnoreDirty()
        val gitTag = getLmdGitTag(ignoreDirty)
        return trimLmdPrefix(gitTag)
    }

    private fun makeVersionCode(
        major: Int,
        minor: Int,
        patch: Int,
        preReleaseType: Int,
        preReleaseVersion: Int
    ): Int {
        val majorString = major.toString()
        val minorString = minor.toString()
        val patchString = patch.toString()
        val preReleaseTypeString = preReleaseType.toString()
        val preReleaseVersionString = preReleaseVersion.toString()

        check(minorString.length <= MINOR_DIGITS)
        check(patchString.length <= PATCH_DIGITS)
        check(preReleaseTypeString.length <= PRE_RELEASE_TYPE_DIGITS)
        check(preReleaseVersionString.length <= PRE_RELEASE_VERSION_DIGITS)

        val versionCodeString = majorString +
                minorString.padStart(MINOR_DIGITS, '0') +
                patchString.padStart(PATCH_DIGITS, '0') +
                preReleaseTypeString.padStart(PRE_RELEASE_TYPE_DIGITS, '0') +
                preReleaseVersionString.padStart(PRE_RELEASE_VERSION_DIGITS, '0')
        return versionCodeString.toInt()
    }

    private fun getSemverVersion(matchResult: MatchResult): Triple<Int, Int, Int> {
        val major = requireNotNull(matchResult.groups[1]).value.toInt()
        val minor = requireNotNull(matchResult.groups[2]).value.toInt()
        val patch = requireNotNull(matchResult.groups[3]).value.toInt()
        return Triple(major, minor, patch)
    }

    private fun makeReleaseVersionCode(matchResult: MatchResult): Int {
        val (major, minor, patch) = getSemverVersion(matchResult)
        return makeVersionCode(
            major,
            minor,
            patch,
            PRE_RELEASE_TYPE_RELEASE,
            preReleaseVersion = 0
        )
    }

    private fun makePreReleaseVersionCode(matchResult: MatchResult): Int {
        val (major, minor, patch) = getSemverVersion(matchResult)
        val preReleaseType = when (matchResult.groups[4]?.value?.lowercase()) {
            "alpha" -> PRE_RELEASE_TYPE_ALPHA
            "beta" -> PRE_RELEASE_TYPE_BETA
            "rc" -> PRE_RELEASE_TYPE_RC
            else -> error("Unknown Pre-Release Type: ${matchResult.groups[4]}")
        }
        val preReleaseVersion = requireNotNull(matchResult.groups[5]).value.toInt()
        return makeVersionCode(major, minor, patch, preReleaseType, preReleaseVersion)
    }

    private fun makeDevelopReleaseVersionCode(matchResult: MatchResult): Int {
        val (major, minor, patch) = getSemverVersion(matchResult)
        return makeVersionCode(
            major,
            minor,
            patch,
            PRE_RELEASE_TYPE_DEVELOP,
            preReleaseVersion = 0
        )
    }

    fun gitTagToVersionCode(gitTag: String): Int {
        val releaseMatch = releaseRegex.matchEntire(gitTag)
        val preReleaseMatch = preReleaseRegex.matchEntire(gitTag)
        val developReleaseMatch = developReleaseRegex.matchEntire(gitTag)
        return when {
            releaseMatch != null -> makeReleaseVersionCode(releaseMatch)
            preReleaseMatch != null -> makePreReleaseVersionCode(preReleaseMatch)
            developReleaseMatch != null -> makeDevelopReleaseVersionCode(developReleaseMatch)
            else -> error(
                "Unable to determine versionCode from $gitTag, which is determined by the last git tag.\n" +
                        "Refer to the README to learn about a proper version format"
            )
        }
    }

    private fun shouldIgnoreDirty(): Boolean {
        return System.getenv()["VERSION_IGNORES_DIRTY_TREE"] != null
    }

    private fun getTazGitTag(ignoreDirty: Boolean): String {
        val command = mutableListOf(
            "git",
            "describe",
            "--tags",
            "--exclude", "lmd-*"
        )

        if (!ignoreDirty) {
            command.add("--dirty")
        }

        return try {
            executeCommand(command)
        } catch (e: NoTagException) {
            error("No tags found. Please run 'git fetch --tags' to get all the remote tags.")
        }
    }

    private fun getLmdGitTag(ignoreDirty: Boolean): String {
        val command = mutableListOf(
            "git",
            "describe",
            "--tags",
            "--match", "lmd-*"
        )

        if (!ignoreDirty) {
            command.add("--dirty")
        }

        return try {
            executeCommand(command)
        } catch (e: NoTagException) {
            // FIXME: temporary solution as long as we dont have any lmd tags in the repo
            "lmd-0.0.1-alpha.1"
        }
    }

    private fun trimLmdPrefix(tag: String): String {
        check(tag.startsWith("lmd-"))
        return tag.substring(4)
    }

    private fun executeCommand(command: List<String>): String {
        val process = ProcessBuilder(command).start()

        val reader = BufferedReader(InputStreamReader(process.inputStream))
        val firstLine: String? = reader.readLine()
        reader.close()

        process.waitFor()
        if (process.exitValue() != 0 ) {

            val errorReader = BufferedReader(InputStreamReader(process.errorStream))
            val error = errorReader.readLine()
            reader.close()

            if (error == "fatal: No names found, cannot describe anything.") {
                throw NoTagException()
            }

            error("Error while executing command: ${command.joinToString(" ")}:\n$error")
        }

        if (firstLine == null || firstLine.isEmpty()) {
            error("Error while executing command: ${command.joinToString(" ")}")
        }

        return firstLine
    }

    private class NoTagException : Exception()
}
