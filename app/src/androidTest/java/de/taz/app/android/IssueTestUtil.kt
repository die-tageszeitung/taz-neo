package de.taz.app.android

import androidx.test.platform.app.InstrumentationRegistry
import com.squareup.moshi.Moshi
import de.taz.app.android.api.dto.IssueDto
import de.taz.app.android.api.dto.WrapperDto
import de.taz.app.android.api.models.Issue
import kotlinx.io.IOException
import java.io.BufferedReader
import java.io.InputStreamReader
import java.text.SimpleDateFormat
import java.util.*

object IssueTestUtil {

    private val moshi = Moshi.Builder().build()
    private val jsonAdapter = moshi.adapter(WrapperDto::class.java)

    private val context = InstrumentationRegistry.getInstrumentation().context
    private val issueDto: IssueDto = jsonAdapter.fromJson(readIssueFromAssets())!!.data.product!!.feedList!!.first().issueList!!.first()

    private val issue = Issue("taz", issueDto)

    fun createIssue(): Issue {
        return issue
    }

    fun createIssue(number: Int): List<Issue> {
        val issueList = mutableListOf<Issue>()
        repeat(number) { i ->
            val issueCopy = issue.copy(date = getYearLater(i))
            issueList.add(issueCopy)
        }
        return issueList
    }

    private fun getYearLater(years: Int): String {
        val dateHelper = SimpleDateFormat("yyyy-MM-dd", Locale.US)

        val calendar = Calendar.getInstance()
        calendar.time = dateHelper.parse(issue.date)!!
        calendar.add(Calendar.YEAR, years)

        return dateHelper.format(calendar.time)
    }

    @Throws(IOException::class)
    private fun readIssueFromAssets(): String {
        val fullFilePath = "testIssue.json"

        var bufferedReader: BufferedReader? = null
        var data = ""
        try {
            bufferedReader = BufferedReader(
                InputStreamReader(
                    context.assets.open(fullFilePath),
                    "UTF-8"
                )
            )

            var line: String? = bufferedReader.readLine()
            while (line != null) {
                data += line
                line = bufferedReader.readLine()
            }
        } finally {
            bufferedReader?.close()
        }
        return data
    }
}
