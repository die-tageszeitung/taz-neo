package de.taz.app.android.api.interfaces

import de.taz.app.android.api.models.IssueStub
import de.taz.app.android.api.models.SectionStub
import de.taz.app.android.persistence.repository.IssueRepository
import de.taz.app.android.persistence.repository.SectionRepository
import de.taz.app.android.singletons.FileHelper
import java.io.File

interface SectionOperations : WebViewDisplayable {

    override val key: String
    val extendedTitle: String?
    val title: String

    val issueStub: IssueStub
        get() = IssueRepository.getInstance().getIssueStubForSection(this.key)

    fun nextSectionStub(): SectionStub? {
        return SectionRepository.getInstance().getNextSectionStub(this.key)
    }

    fun previousSectionStub(): SectionStub? {
        return SectionRepository.getInstance().getPreviousSectionStub(this.key)
    }

    override fun getFile(): File? {
        return FileHelper.getInstance().getFile(this.key)
    }

    override fun previous(): SectionStub? {
        return previousSectionStub()
    }

    override fun next(): SectionStub? {
        return nextSectionStub()
    }

    fun getHeaderTitle(): String {
        return extendedTitle ?: title
    }

    override fun getIssueOperations() = issueStub

}