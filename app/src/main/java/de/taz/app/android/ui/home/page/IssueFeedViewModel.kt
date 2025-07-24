package de.taz.app.android.ui.home.page

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import de.taz.app.android.api.models.Feed
import de.taz.app.android.dataStore.GeneralDataStore
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import java.util.Date
import java.util.LinkedList

typealias MomentChangedListener = (Date) -> Unit

const val KEY_CURRENT_DATE = "KEY_CURRENT_DATE"

class IssueFeedViewModel(
    application: Application,
    savedStateHandle: SavedStateHandle
) : AndroidViewModel(application) {
    private val notifyMomentChangedListeners = LinkedList<MomentChangedListener>()
    private val generalDataStore = GeneralDataStore.getInstance(application)

    val currentDateLiveData = savedStateHandle.getLiveData<Date>(KEY_CURRENT_DATE)

    val pdfMode = generalDataStore.pdfMode.asFlow()

    @Deprecated("use Flow instead", ReplaceWith("pdfMode"))
    val pdfModeLiveData: LiveData<Boolean> = generalDataStore.pdfMode.asLiveData()

    fun togglePdfMode() = viewModelScope.launch {
        generalDataStore.pdfMode.set(!getPdfMode())
    }

    fun getPdfMode() = requireNotNull(pdfModeLiveData.value) {
        "PdfMode is always set - no null possible"
    }

    fun setFeed(feed: Feed) {
        if (!Feed.equalsShallow(mutableFeedLiveData.value, feed)) {
            mutableFeedLiveData.value = feed
        }
    }

    private val mutableFeedLiveData = MutableLiveData<Feed>()
    val feed: LiveData<Feed> = mutableFeedLiveData

    private val _forceRefreshTimeMs = MutableLiveData<Long>(0L)
    val forceRefreshTimeMs: LiveData<Long> = _forceRefreshTimeMs

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