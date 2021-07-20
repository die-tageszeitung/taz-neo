package de.taz.app.android.ui.home.page

import android.app.Application
import android.content.Context
import androidx.lifecycle.*
import de.taz.app.android.PREFERENCES_GENERAL
import de.taz.app.android.SETTINGS_SHOW_PDF_AS_MOMENT
import de.taz.app.android.api.models.Feed
import de.taz.app.android.util.SharedPreferenceBooleanLiveData
import java.util.*

typealias MomentChangedListener = (Date) -> Unit

const val KEY_CURRENT_DATE = "KEY_CURRENT_DATE"

class IssueFeedViewModel(
    application: Application,
    savedStateHandle: SavedStateHandle
) : AndroidViewModel(application) {
    private val notifyMomentChangedListeners = LinkedList<MomentChangedListener>()
    private val preferences = application.getSharedPreferences(PREFERENCES_GENERAL, Context.MODE_PRIVATE)
    val currentDate = savedStateHandle.getLiveData<Date>(KEY_CURRENT_DATE)

    val pdfMode: MutableLiveData<Boolean> = SharedPreferenceBooleanLiveData(
        preferences, SETTINGS_SHOW_PDF_AS_MOMENT, false
    )

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