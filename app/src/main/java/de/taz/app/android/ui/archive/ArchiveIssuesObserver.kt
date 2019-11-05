package de.taz.app.android.ui.archive

import android.graphics.BitmapFactory
import androidx.lifecycle.Observer
import androidx.lifecycle.lifecycleScope
import de.taz.app.android.api.models.IssueStub
import de.taz.app.android.api.models.Moment
import de.taz.app.android.persistence.repository.MomentRepository
import de.taz.app.android.util.FileHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ArchiveIssuesObserver(private val archivePresenter: ArchivePresenter) :
    Observer<List<IssueStub>?> {

    override fun onChanged(issueStubs: List<IssueStub>?) {
        archivePresenter.getView()?.onDataSetChanged(issueStubs ?: emptyList())

        issueStubs?.forEach { issueStub ->
            if (archivePresenter.viewModel?.getBitmap(issueStub.tag) == null) {
                generateMomentBitmapForIssueStub(issueStub)
            }
        }
    }

    private fun generateMomentBitmapForIssueStub(issueStub: IssueStub) {
        archivePresenter.getView()?.getLifecycleOwner()?.lifecycleScope?.launch(Dispatchers.IO) {
            MomentRepository.getInstance().get(issueStub)?.let { moment ->
                val observer = ArchiveMomentDownloadObserver(archivePresenter, issueStub, moment)

                withContext(Dispatchers.Main) {
                    archivePresenter.getView()?.getLifecycleOwner()?.let {
                        moment.isDownloadedLiveData().observe(
                            it, observer
                        )
                    }
                }
            }
        }
    }

}

class ArchiveMomentDownloadObserver(
    private val archivePresenter: ArchivePresenter,
    private val issueStub: IssueStub,
    private val moment: Moment
) : Observer<Boolean> {

    override fun onChanged(isDownloaded: Boolean?) {
        if (isDownloaded == true) {
            moment.isDownloadedLiveData().removeObserver(this@ArchiveMomentDownloadObserver)
            archivePresenter.getView()?.getLifecycleOwner()
                ?.lifecycleScope?.launch(Dispatchers.IO) {
                generateBitMapForMoment(issueStub, moment)
            }
        }
    }

    private fun generateBitMapForMoment(issueStub: IssueStub, moment: Moment) {
        moment.imageList.lastOrNull()?.let {
            FileHelper.getInstance().getFile("${issueStub.tag}/${it.name}").let { imgFile ->
                if (imgFile.exists()) {
                    archivePresenter.onMomentBitmapCreated(
                        issueStub.tag,
                        BitmapFactory.decodeFile(imgFile.absolutePath)
                    )
                }
            }
        }
    }

}