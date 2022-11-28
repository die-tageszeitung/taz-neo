package de.taz.app.android

import androidx.test.platform.app.InstrumentationRegistry
import de.taz.app.android.api.dto.IssueDto
import de.taz.app.android.api.dto.ProductDto
import de.taz.app.android.api.dto.WrapperDto
import de.taz.app.android.api.mappers.IssueMapper
import de.taz.app.android.api.mappers.ResourceInfoMapper
import de.taz.app.android.api.models.Issue
import de.taz.app.android.api.models.ResourceInfo
import kotlinx.serialization.encodeToString
import de.taz.app.android.util.Json
import kotlinx.serialization.json.decodeFromStream
import java.io.*

object TestDataUtil {
    private val context = InstrumentationRegistry.getInstrumentation().context

    fun getIssue(fullFilePath: String = "testIssue"): Issue {
        val issueDto: IssueDto =
            Json.decodeFromStream<WrapperDto>(getInputStream(fullFilePath)).data!!.product!!.feedList!!.first().issueList!!.first()
        return IssueMapper.from("taz", issueDto)
    }

    fun getResourceInfo(): ResourceInfo {
        val productDto = Json.decodeFromStream<WrapperDto>(getInputStream("resourceInfo")).data!!.product!!
        return ResourceInfoMapper.from(productDto)
    }

    private fun getInputStream(fileName: String): InputStream {
        val fullFilePath = if (fileName.endsWith(".json")) fileName else "$fileName.json"
        return BufferedInputStream(context.assets.open(fullFilePath))
    }
}
