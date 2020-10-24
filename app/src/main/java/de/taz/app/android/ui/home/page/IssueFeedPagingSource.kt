package de.taz.app.android.ui.home.page

import androidx.paging.PagingSource
import de.taz.app.android.api.models.Feed
import de.taz.app.android.api.models.IssueStatus
import de.taz.app.android.api.models.IssueStub
import de.taz.app.android.data.DataService
import de.taz.app.android.simpleDateFormat
import de.taz.app.android.singletons.DateHelper
import java.util.*

class IssueFeedPagingSource(
    private val feeds: List<Feed>,
    private val feedStatus: IssueStatus,
    private val dataService: DataService,
    private val chunkSize: Int = 10
): PagingSource<Date, IssueStub>() {
    private val maxDate = feeds.map { simpleDateFormat.parse(it.issueMaxDate) }.maxOrNull()
    private val minDate = feeds.map { simpleDateFormat.parse(it.issueMinDate) }.minOrNull()

    override suspend fun load(params: LoadParams<Date>): LoadResult<Date, IssueStub> {
        try {
            // Start with key of today
            val startDate = params.key ?: Date()
            var issues = dataService.getIssueStubsByFeed(startDate, feeds.map { it.name }, feedStatus, chunkSize)
            if (issues.isEmpty()) {
                issues = dataService.getIssueStubsByFeed(startDate, feeds.map { it.name }, feedStatus, chunkSize, allowCache = false)
            }
            val issueDates = issues.map { simpleDateFormat.parse(it.date) }
            val dayAfterNewest = issueDates.maxOrNull()?.let{
                DateHelper.addDays(it, chunkSize)
            }


            val dayBeforeOldest = issueDates.minOrNull()?.let{
                DateHelper.subDays(it, 1)
            }
            val prevKey = if (issueDates.any { it!! <= minDate  }) {
                null
            } else {
                dayBeforeOldest
            }

            val nextKey = if (issueDates.any { it!! >= maxDate }) {
                null
            } else {
                dayAfterNewest
            }

            return LoadResult.Page(
                data = issues,
                prevKey = prevKey,
                nextKey = nextKey
            )
        } catch (e: Exception) {
            return LoadResult.Error(e)
        }
    }
}