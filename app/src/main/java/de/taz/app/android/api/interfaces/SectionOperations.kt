package de.taz.app.android.api.interfaces

import de.taz.app.android.api.models.Issue
import de.taz.app.android.api.models.IssueStub
import de.taz.app.android.api.models.Section
import de.taz.app.android.api.models.SectionStub
import de.taz.app.android.persistence.repository.IssueRepository
import de.taz.app.android.persistence.repository.SectionRepository

interface SectionOperations {

    val sectionFileName: String

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

}