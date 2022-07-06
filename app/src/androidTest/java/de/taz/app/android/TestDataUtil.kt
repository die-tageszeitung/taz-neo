package de.taz.app.android

import androidx.test.platform.app.InstrumentationRegistry
import de.taz.app.android.api.dto.IssueDto
import de.taz.app.android.api.dto.ProductDto
import de.taz.app.android.api.dto.WrapperDto
import de.taz.app.android.api.models.Issue
import de.taz.app.android.api.models.ResourceInfo
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.IOException
import java.io.BufferedReader
import java.io.InputStreamReader

object TestDataUtil {

    private val jsonAdapter = JsonHelper.adapter<WrapperDto>()

    private val context = InstrumentationRegistry.getInstrumentation().context

    fun getIssue(fullFilePath: String = "testIssue"): Issue {
        val issueDto: IssueDto = jsonAdapter.fromJson(readFromAssets(fullFilePath))!!.data!!.product!!.feedList!!.first().issueList!!.first()
        return Issue("taz", issueDto)
    }

    fun getResourceInfo(): ResourceInfo {
        val resourceInfo = ResourceInfo(jsonAdapter.fromJson(readFromAssets("resourceInfo"))!!.data!!.product!!)
        return resourceInfo
    }

    @Throws(IOException::class)
    private fun readFromAssets(fileName: String): String {
        val fullFilePath = if (fileName.endsWith(".json")) fileName else "$fileName.json"

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
