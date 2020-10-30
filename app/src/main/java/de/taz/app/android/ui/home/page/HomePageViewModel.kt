package de.taz.app.android.ui.home.page

import android.app.Application
import androidx.lifecycle.*
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.cachedIn
import de.taz.app.android.api.models.Feed
import de.taz.app.android.api.models.IssueStatus
import de.taz.app.android.data.DataService
import kotlinx.coroutines.flow.Flow

open class HomePageViewModel(application: Application) : AndroidViewModel(application) {

    fun getPagerForFeed(feed: Feed, status: IssueStatus): Flow<PagingData<IssueStubViewData>> {
        return Pager(PagingConfig(
            pageSize = 6,
            enablePlaceholders = true,
        )) {
            IssueFeedPagingSource(
                feed,
                status,
                DataService.getInstance()
            )
        }.flow.cachedIn(viewModelScope)
    }
}