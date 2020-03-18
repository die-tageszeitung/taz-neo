package de.taz.app.android.api.interfaces

import de.taz.app.android.api.models.Issue
import de.taz.app.android.api.models.IssueStub
import de.taz.app.android.api.models.Section
import de.taz.app.android.api.models.SectionStub
import de.taz.app.android.persistence.repository.IssueRepository
import de.taz.app.android.persistence.repository.SectionRepository
import de.taz.app.android.singletons.FileHelper
import java.io.File

interface SectionOperations : WebViewDisplayable {

    val sectionFileName: String
    val extendedTitle: String?
    val title: String

    val issueStub: IssueStub
        get() = IssueRepository.getInstance().getIssueStubForSection(sectionFileName)

    fun nextSectionStub(): SectionStub? {
        return SectionRepository.getInstance().getNextSectionStub(sectionFileName)
    }

    fun previousSectionStub(): SectionStub? {
        return SectionRepository.getInstance().getPreviousSectionStub(sectionFileName)
    }

    fun nextSection(): Section? {
        return SectionRepository.getInstance().getNextSection(sectionFileName)
    }

    fun previousSection(): Section? {
        return SectionRepository.getInstance().getPreviousSection(sectionFileName)
    }

    fun getIssue(): Issue {
        return IssueRepository.getInstance().getIssueForSection(sectionFileName)
    }

    override fun getFile(): File? {
        return FileHelper.getInstance().getFile(sectionFileName)
    }

    override fun previous(): Section? {
        return previousSection()
    }

    override fun next(): Section? {
        return nextSection()
    }

    fun getHeaderTitle(): String {
        return extendedTitle ?: title
    }

    override fun getIssueOperations() = issueStub

}