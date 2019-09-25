package de.taz.app.android.api.interfaces

interface IssueFunctions: Downloadable {

    val feedName: String
    val date: String
    val fileList: List<String>

    val tag: String
        get() = "$feedName/$date"

    val issueFileList: List<String>
        get() = fileList.filter { !it.startsWith("/global/") }

    val globalFileList: List<String>
        get() = fileList.filter { it.startsWith("/global/") }.map { it.split("/").last() }

    override fun getAllFileNames(): List<String> {
        return listOf(issueFileList, globalFileList).flatten()
    }

}