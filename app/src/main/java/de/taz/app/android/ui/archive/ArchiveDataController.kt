package de.taz.app.android.ui.archive

import android.graphics.Bitmap
import androidx.lifecycle.*
import de.taz.app.android.api.models.IssueStub
import de.taz.app.android.base.BaseDataController
import de.taz.app.android.persistence.repository.IssueRepository

open class ArchiveDataController : BaseDataController(), ArchiveContract.DataController {

    private val issueLiveData: LiveData<List<IssueStub>> =
        IssueRepository.getInstance().getAllStubsLiveData()

    private val issueMomentBitmapMap = mutableMapOf<String, Bitmap>()

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

    override fun getMomentBitmapMap(): Map<String, Bitmap> {
        return issueMomentBitmapMap
    }

    override fun addBitmap(tag: String, bitmap: Bitmap) {
        issueMomentBitmapMap[tag] =  bitmap
    }

    override fun getBitmap(tag: String): Bitmap? {
        return issueMomentBitmapMap[tag]
    }

}