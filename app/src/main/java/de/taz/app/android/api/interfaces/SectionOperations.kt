package de.taz.app.android.api.interfaces

import android.content.Context
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

    fun getIssueStub(applicationContext: Context): IssueStub? {
        return IssueRepository.getInstance(applicationContext).getIssueStubForSection(key)
    }

    suspend fun getNavButton(applicationContext: Context): Image? = withContext(Dispatchers.IO) {
        return@withContext SectionRepository.getInstance(applicationContext)
            .getNavButtonForSection(this@SectionOperations.key)
    }


}