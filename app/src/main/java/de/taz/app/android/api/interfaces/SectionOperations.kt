package de.taz.app.android.api.interfaces

import de.taz.app.android.api.models.IssueStub
import de.taz.app.android.api.models.Section
import de.taz.app.android.api.models.SectionBase
import de.taz.app.android.persistence.repository.IssueRepository
import de.taz.app.android.persistence.repository.SectionRepository

interface SectionOperations {

    val sectionFileName: String

    val issueStub: IssueStub
        get() = IssueRepository.getInstance().getIssueBaseForSection(sectionFileName)

    fun nextSectionBase(): SectionBase? {
        return SectionRepository.getInstance().getNextSectionBase(sectionFileName)
    }

    fun previousSectionBase(): SectionBase? {
        return SectionRepository.getInstance().getPreviousSectionBase(sectionFileName)
    }

    fun nextSection(): Section? {
        return SectionRepository.getInstance().getNextSection(sectionFileName)
    }

    fun previousSection(): Section? {
        return SectionRepository.getInstance().getPreviousSection(sectionFileName)
    }

}