package de.taz.test

import de.taz.app.android.api.dto.IssueDto
import de.taz.app.android.api.dto.WrapperDto
import de.taz.app.android.api.mappers.IssueMapper
import de.taz.app.android.api.models.Image
import de.taz.app.android.api.models.Issue
import de.taz.app.android.persistence.repository.ImageRepository
import de.taz.app.android.util.Json
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.decodeFromStream
import java.io.InputStream

object TestDataUtil {

    fun getIssue(fullFilePath: String = "testIssue"): Issue {
        val issueDto: IssueDto =
            Json.decodeFromStream<WrapperDto>(getInputStream(fullFilePath)).data!!.product!!.feedList!!.first().issueList!!.first()
        return IssueMapper.from("taz", issueDto)
    }

    fun createDefaultNavButton(imageRepository: ImageRepository): Image {
        val issue = getIssue()
        // Assumes every navButton in the testdata is the defaultNavButton
        val defaultNavButton = issue.sectionList.first().navButton
        runBlocking {
            imageRepository.saveInternal(defaultNavButton)
        }
        return defaultNavButton
    }

    private fun getInputStream(fileName: String): InputStream {
        var fullFilePath = fileName
        if (!fullFilePath.endsWith(".json")) {
            fullFilePath += ".json"
        }
        if (!fullFilePath.startsWith("/")) {
            fullFilePath = "/$fullFilePath"
        }
        val inputStream = requireNotNull(javaClass.getResourceAsStream(fullFilePath))
        return inputStream.buffered()
    }
}