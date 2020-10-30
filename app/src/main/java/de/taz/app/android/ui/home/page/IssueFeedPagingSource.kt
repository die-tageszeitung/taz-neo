package de.taz.app.android.ui.home.page

import androidx.paging.PagingSource
import de.taz.app.android.api.models.*
import de.taz.app.android.data.DataService
import de.taz.app.android.persistence.repository.FeedRepository
import de.taz.app.android.simpleDateFormat
import de.taz.app.android.singletons.FileHelper
import de.taz.app.android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.lang.IllegalStateException
import java.util.*

class IssueFeedPagingSource(
    private val feed: Feed,
    private val feedStatus: IssueStatus,
    private val dataService: DataService
): PagingSource<Date, IssueStubViewData>() {
    private val issuePaging = StupidIssuePaging(feed, dataService)
    private val log by Log


    override suspend fun load(params: LoadParams<Date>): LoadResult<Date, IssueStubViewData> = withContext(Dispatchers.IO) {
        try {
            val key = params.key ?: Date()
            val issueStub = dataService.getIssueStubsByFeedAndStatus(key, listOf(feed.name), feedStatus, 1).firstOrNull()
            if (issueStub == null) {
                val hint = "Expected an issue at $key but none was found"
                log.error(hint)
                throw IllegalStateException(hint)
            }
            val viewData = dataService.getMoment(issueStub.issueKey)?.let { moment ->
                val imageUri = moment.getMomentImage()?.let {
                    FileHelper.getInstance().getAbsoluteFilePath(FileEntry(it))
                }

                dataService.ensureDownloaded(moment)

                val dimension = FeedRepository.getInstance().get(moment.issueFeedName).momentRatioAsDimensionRatioString()

                IssueStubViewData(
                    issueStub = issueStub,
                    downloadStatus = if (issueStub.dateDownload != null) DownloadStatus.done else DownloadStatus.pending,
                    momentImageUri = imageUri,
                    dimension = dimension
                )
            } ?: throw IllegalStateException("Could not get moment for issue")

            LoadResult.Page(
                data = listOf(viewData),
                prevKey = issuePaging.findNext(key),
                nextKey = issuePaging.findPrevious(key),
                itemsAfter = issuePaging.itemsAfter(issueDate = simpleDateFormat.parse(issueStub.date)!!),
                itemsBefore = issuePaging.itemsBefore(issueDate = simpleDateFormat.parse(issueStub.date)!!)
            )
        } catch (e: Exception) {
            LoadResult.Error(e)
        }
    }
}