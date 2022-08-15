package de.taz.app.android.ui.home.page

import android.app.Application
import androidx.lifecycle.*
import de.taz.app.android.api.models.Feed
import de.taz.app.android.dataStore.GeneralDataStore
import kotlinx.coroutines.flow.last
import kotlinx.coroutines.launch
import java.util.*

typealias MomentChangedListener = (Date) -> Unit

const val KEY_CURRENT_DATE = "KEY_CURRENT_DATE"

class IssueFeedViewModel(
    application: Application,
    savedStateHandle: SavedStateHandle
) : AndroidViewModel(application) {
    private val notifyMomentChangedListeners = LinkedList<MomentChangedListener>()
    private val generalDataStore = GeneralDataStore.getInstance(application)

    val currentDate = savedStateHandle.getLiveData<Date>(KEY_CURRENT_DATE)

    val pdfModeLiveData: LiveData<Boolean> = generalDataStore.pdfMode.asLiveData()

    fun togglePdfMode() = viewModelScope.launch {
        generalDataStore.pdfMode.set(!getPdfMode())
    }

    fun getPdfMode() = requireNotNull(pdfModeLiveData.value) {
        "PdfMode is always set - no null possible"
    }

    fun setFeed(feed: Feed) {
        mutableFeedLiveData.value = feed
    }

    private val mutableFeedLiveData = MutableLiveData<Feed>()
    val feed: LiveData<Feed> = mutableFeedLiveData


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