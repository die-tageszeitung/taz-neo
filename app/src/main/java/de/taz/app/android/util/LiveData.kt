package de.taz.app.android.util

import android.content.Context
import androidx.lifecycle.LiveData
import androidx.lifecycle.MediatorLiveData
import androidx.lifecycle.asLiveData
import de.taz.app.android.content.ContentService
import de.taz.app.android.content.cache.CacheStateUpdate
import de.taz.app.android.persistence.repository.AbstractIssuePublication
import de.taz.app.android.persistence.repository.IssueKey
import de.taz.app.android.persistence.repository.IssueRepository
import de.taz.app.android.singletons.AuthHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch


class IssuePublicationMonitor(
    applicationContext: Context,
    publication: AbstractIssuePublication
) {
    private val authHelper = AuthHelper.getInstance(applicationContext)
    private val contentService = ContentService.getInstance(applicationContext)

    val issueCacheLiveData: MediatorLiveData<CacheStateUpdate> =
        MediatorLiveData<CacheStateUpdate>().apply {
            addSource(authHelper.minStatusLiveData) { minStatus ->
                CoroutineScope(Dispatchers.Default).launch {
                    postValue(
                        contentService.getCacheState(publication)
                    )
                }
            }

            addSource(
                contentService
                    .getCacheStatusFlow(publication)
                    .asLiveData()
            ) { postValue(it) }
        }

}