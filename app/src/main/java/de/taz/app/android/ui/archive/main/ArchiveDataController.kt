package de.taz.app.android.ui.archive.main

import android.graphics.Bitmap
import androidx.lifecycle.*
import de.taz.app.android.api.models.Feed
import de.taz.app.android.api.models.IssueStub
import de.taz.app.android.base.BaseDataController
import de.taz.app.android.persistence.repository.FeedRepository
import de.taz.app.android.persistence.repository.IssueRepository
import de.taz.app.android.util.PREFERENCES_FEEDS_INACTIVE
import de.taz.app.android.util.PreferencesHelper
import de.taz.app.android.util.SharedPreferenceStringSetLiveData

open class ArchiveDataController : BaseDataController(),
    ArchiveContract.DataController {

    /**
     * issues to be shown
     */
    private val issueLiveData: LiveData<List<IssueStub>> =
        IssueRepository.getInstance().getAllStubsLiveData()

    override fun getIssueStubs(): List<IssueStub>? {
        return issueLiveData.value
    }

    override fun observeIssueStubs(
        lifeCycleOwner: LifecycleOwner,
        observer: Observer<List<IssueStub>?>
    ) {
        issueLiveData.observe(lifeCycleOwner, observer)
    }

    override fun observeIssueStubs(
        lifeCycleOwner: LifecycleOwner,
        observationCallback: (List<IssueStub>?) -> Unit
    ) {
        issueLiveData.observe(
            lifeCycleOwner,
            Observer { issues -> observationCallback.invoke(issues) }
        )
    }

    /**
     * map of [de.taz.app.android.api.interfaces.IssueOperations.tag]
     * to [Bitmap] of [de.taz.app.android.api.models.Moment]
     */
    private val issueMomentBitmapMap = mutableMapOf<String, Bitmap>()

    override fun getMomentBitmapMap(): Map<String, Bitmap> {
        return issueMomentBitmapMap
    }

    override fun addBitmap(tag: String, bitmap: Bitmap) {
        issueMomentBitmapMap[tag] =  bitmap
    }

    override fun getBitmap(tag: String): Bitmap? {
        return issueMomentBitmapMap[tag]
    }

    /**
     * feeds to be used in filtering and endNavigationView
     */
    private val feedsLiveData: LiveData<List<Feed>> =
        FeedRepository.getInstance().getAllLiveData()

    override fun observeFeeds(
        lifeCycleOwner: LifecycleOwner,
        observationCallback: (List<Feed>?) -> Unit
    ) {
        feedsLiveData.observe(
            lifeCycleOwner,
            Observer { feeds -> observationCallback.invoke(feeds) }
        )
    }

    override fun observeFeeds(lifeCycleOwner: LifecycleOwner, observer: Observer<List<Feed>?>) {
        feedsLiveData.observe(lifeCycleOwner, observer)
    }

    /**
     * Set of [String] corresponding to the deactivated [Feed]'s [Feed.name]
     */
    private val inactiveFeedNameLiveData = SharedPreferenceStringSetLiveData(
        PreferencesHelper.getInstance().feedPreferences, PREFERENCES_FEEDS_INACTIVE, emptySet()
    )

    override fun observeInactiveFeedNames(
        lifeCycleOwner: LifecycleOwner,
        observationCallback: (Set<String>) -> Unit
    ) {
        inactiveFeedNameLiveData.observe(
            lifeCycleOwner,
            Observer { feeds -> observationCallback.invoke(feeds) }
        )
    }

    override fun observeInactiveFeedNames(lifeCycleOwner: LifecycleOwner, observer: Observer<Set<String>>) {
        inactiveFeedNameLiveData.observe(lifeCycleOwner, observer)
    }

}