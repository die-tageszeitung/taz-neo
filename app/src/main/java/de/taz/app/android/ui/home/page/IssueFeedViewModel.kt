package de.taz.app.android.ui.home.page

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import de.taz.app.android.api.models.Feed
import de.taz.app.android.dataStore.GeneralDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.util.Date
import java.util.LinkedList

typealias MomentChangedListener = (Date) -> Unit

const val KEY_CURRENT_DATE = "KEY_CURRENT_DATE"
const val KEY_FEED = "KEY_FEED"
const val KEY_REFRESH_VIEW_ENABLED = "KEY_REFRESH_VIEW_ENABLED"

class IssueFeedViewModel(
    application: Application,
    savedStateHandle: SavedStateHandle
) : AndroidViewModel(application) {
    private val notifyMomentChangedListeners = LinkedList<MomentChangedListener>()
    private val generalDataStore = GeneralDataStore.getInstance(application)

    val currentDate = savedStateHandle.getMutableStateFlow(KEY_CURRENT_DATE, Date())

    val refreshViewEnabled = savedStateHandle.getMutableStateFlow(KEY_REFRESH_VIEW_ENABLED, true)

    val pdfMode = generalDataStore.pdfMode.asFlow().distinctUntilChanged()

    fun togglePdfMode() = viewModelScope.launch {
        generalDataStore.pdfMode.set(!generalDataStore.pdfMode.get())
    }

    suspend fun getPdfMode() = pdfMode.first()

    suspend fun setFeed(feed: Feed) {
        if (!Feed.equalsShallow(_mutableFeedFlow.value, feed)) {
            _mutableFeedFlow.emit(feed)
        }
    }

    private val _mutableFeedFlow = savedStateHandle.getMutableStateFlow<Feed?>(KEY_FEED, null)
    val feed: Flow<Feed> = _mutableFeedFlow.filterNotNull()

    private val _forceRefreshTimeMs = MutableStateFlow<Long>(0L)
    val forceRefreshTimeMs: Flow<Long> = _forceRefreshTimeMs.asStateFlow()

    /**
     *  Triggers a force refresh with a redraw of the feed list (e.g. carousel).
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
}