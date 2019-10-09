package de.taz.app.android.api.interfaces

interface IssueOperations {

    val baseUrl: String
    val feedName: String
    val date: String

    val tag: String
        get() = "$feedName/$date"

}