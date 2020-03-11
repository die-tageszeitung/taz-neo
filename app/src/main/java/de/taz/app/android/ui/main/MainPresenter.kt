package de.taz.app.android.ui.main

import android.os.Bundle
import androidx.lifecycle.lifecycleScope
import de.taz.app.android.api.models.IssueStub
import de.taz.app.android.base.BasePresenter
import de.taz.app.android.download.DownloadService
import de.taz.app.android.persistence.repository.IssueRepository
import de.taz.app.android.ui.drawer.sectionList.SectionDrawerFragment
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.lang.ref.WeakReference

class MainPresenter: MainContract.Presenter, BasePresenter<MainContract.View, MainDataController>(
    MainDataController::class.java
) {

    private var sectionDrawerFragmentReference: WeakReference<SectionDrawerFragment>? = null

    override fun onViewCreated(savedInstanceState: Bundle?) {
        getView()?.apply {
            getLifecycleOwner().lifecycleScope.launch(Dispatchers.IO) {
                IssueRepository.getInstance().getLatestIssueStub()?.let { issueStub ->
                    viewModel?.setIssueOperations(issueStub)
                }
            }
            // only show archive if created in the beginning else show current fragment
            if (savedInstanceState == null || sectionDrawerFragmentReference == null) {
                createSectionDrawerFragment()
                sectionDrawerFragmentReference?.get()?.let {
                    showDrawerFragment(it)
                }
            }
        }
    }

    override fun setDrawerIssue() {
        viewModel?.getIssueStub()?.let {
            sectionDrawerFragmentReference?.get()?.setIssueStub(it) ?: run {
                val sectionDrawerFragment = createSectionDrawerFragment()
                sectionDrawerFragment.setIssueStub(it)
            }
        }
    }

    private fun createSectionDrawerFragment(): SectionDrawerFragment {
        val sectionDrawerFragment = SectionDrawerFragment()
        sectionDrawerFragmentReference = WeakReference(sectionDrawerFragment)
        return sectionDrawerFragment
    }


    override fun showIssue(issueStub: IssueStub) {
        getView()?.getLifecycleOwner()?.lifecycleScope?.launch(Dispatchers.IO) {
            val issue = IssueRepository.getInstance().getIssue(issueStub)

            // start download if not yet downloaded
            if (!issue.isDownloaded()) {
                DownloadService.getInstance().download(issue)
            }

            // set main issue
            getView()?.getMainDataController()?.setIssueOperations(issueStub)

            issue.sectionList.first().let { firstSection ->
                getView()?.showInWebView(firstSection)
            }
        }
    }
}
