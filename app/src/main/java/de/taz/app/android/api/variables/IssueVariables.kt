package de.taz.app.android.api.variables

import com.squareup.moshi.Moshi

data class IssueVariables(val feedName: String, val issueDate: String): Variables {

    override fun toJson(): String {
        val moshi = Moshi.Builder().build()
        val adapter = moshi.adapter(IssueVariables::class.java)

        return adapter.toJson(this)
    }
}