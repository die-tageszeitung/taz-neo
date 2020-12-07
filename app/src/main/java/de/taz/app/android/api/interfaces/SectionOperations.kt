package de.taz.app.android.api.interfaces

import de.taz.app.android.api.models.Image
import de.taz.app.android.api.models.IssueStub
import de.taz.app.android.persistence.repository.IssueRepository
import de.taz.app.android.persistence.repository.SectionRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

interface SectionOperations {

    val key: String
    val extendedTitle: String?
    val title: String

    fun getHeaderTitle(): String {
        return extendedTitle ?: title
    }

    fun getIssueStub(): IssueStub? {
        return IssueRepository.getInstance().getIssueStubForSection(key)
    }

    suspend fun getNavButton(): Image? = withContext(Dispatchers.IO) {
        return@withContext SectionRepository.getInstance().getNavButtonForSection(this@SectionOperations.key)
    }


}