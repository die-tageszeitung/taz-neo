package de.taz.app.android.ui.home.page

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import de.taz.app.android.BuildConfig
import de.taz.app.android.api.models.Feed
import de.taz.app.android.content.FeedService
import de.taz.app.android.dataStore.GeneralDataStore
import de.taz.app.android.simpleDateFormat
import de.taz.app.android.util.Log
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.util.Date
import java.util.LinkedList

typealias MomentChangedListener = (Date) -> Unit

const val KEY_REFRESH_VIEW_ENABLED = "KEY_REFRESH_VIEW_ENABLED"
const val KEY_ARCHIVE_APP_BAR_VISIBLE = "KEY_ARCHIVE_APP_BAR_VISIBLE"
const val KEY_LAST_FEED = "KEY_LAST_FEED"

class IssueFeedViewModel(
    application: Application,
    savedStateHandle: SavedStateHandle
) : AndroidViewModel(application) {
    private val notifyMomentChangedListeners = LinkedList<MomentChangedListener>()
    private val generalDataStore = GeneralDataStore.getInstance(application)
    private val feedService = FeedService.getInstance(application)
    private val log by Log

    private val _mutableRequestDateFocus = MutableSharedFlow<Date>(
        replay = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    val requestDateFocus: Flow<Date> = _mutableRequestDateFocus

    /**
     * set [requestDateFocus] to date if not already matching
     */
    fun requestDateFocus(date: Date) = viewModelScope.launch {
        _mutableRequestDateFocus.emit(date)
    }

    /**
     * update [requestDateFocus] by parsing a string in yyyy-MM-dd format
     * if date does not already match
     */
    fun requestDateFocus(simpleDateString: String) {
        val date = simpleDateFormat.parse(simpleDateString)
        if (date != null) {
            requestDateFocus(date)
        } else {
            throw IllegalArgumentException("updateCurrentDate called with wrong string: $simpleDateString")
        }
    }

    suspend fun requestNewestDateFocus() {
        feed.first().publicationDates.firstOrNull()?.let {
            requestDateFocus(it.date)
        }
    }


    val refreshViewEnabled = savedStateHandle.getMutableStateFlow(KEY_REFRESH_VIEW_ENABLED, true)

    val appBarVisible = savedStateHandle.getMutableStateFlow(KEY_ARCHIVE_APP_BAR_VISIBLE, true)
     val lastFeed = savedStateHandle.getMutableStateFlow(KEY_LAST_FEED, BuildConfig.DISPLAYED_FEED)

    val pdfModeFlow = generalDataStore.pdfMode.asFlow().distinctUntilChanged()

    suspend fun getPdfMode() = pdfModeFlow.first()

    private val _feed = MutableStateFlow<Feed?>(null)
    val feed: Flow<Feed> = _feed.asStateFlow().filterNotNull()

    suspend fun getAllFeeds() = feedService.getAllFeeds().first()

    private var feedCollectionJob: Job? = null

    init {
        viewModelScope.launch {
            lastFeed.value = generalDataStore.lastFeedSet.get()
            observeFeed(lastFeed.value)
        }
    }
    private fun observeFeed(name: String) {
        feedCollectionJob?.cancel()
        feedCollectionJob = viewModelScope.launch {
            feedService.getFeedFlow(name).collect {
                _feed.value = it
            }
        }
    }

    private val _forceRefreshTimeMs = MutableStateFlow(0L)
    val forceRefreshTimeMs: Flow<Long> = _forceRefreshTimeMs.asStateFlow()

    /**
     *  Triggers a force refresh with a redraw of the feed list (e.g. carousel).
     *  This might e.g. be desirable if placeholders are shown for a moment.
     */
    fun forceRefresh() {
        _forceRefreshTimeMs.value = System.currentTimeMillis()
    }

    fun addNotifyMomentChangedListener(listener: MomentChangedListener): MomentChangedListener {
        notifyMomentChangedListeners.add(listener)
        return listener
    }

    fun removeNotifyMomentChangedListener(listener: MomentChangedListener) {
        notifyMomentChangedListeners.remove(listener)
    }

    fun notifyMomentChanged(date: Date) {
        notifyMomentChangedListeners.forEach { it.invoke(date) }
    }

    fun switchFeed(name: String) {
        if (lastFeed.value == name) return
        lastFeed.value = name
        log.debug("Switched to feed: $feed")
        // Persist to DataStore for future app starts
        viewModelScope.launch {
            generalDataStore.lastFeedSet.set(name)
        }
        observeFeed(name)
    }
}