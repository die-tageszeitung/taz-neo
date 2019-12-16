package de.taz.app.android.ui.main

import android.os.Bundle
import androidx.lifecycle.lifecycleScope
import de.taz.app.android.base.BasePresenter
import de.taz.app.android.persistence.repository.IssueRepository
import de.taz.app.android.ui.drawer.sectionList.SectionDrawerFragment
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class MainPresenter: MainContract.Presenter, BasePresenter<MainContract.View, MainDataController>(
    MainDataController::class.java
) {

    override fun onViewCreated(savedInstanceState: Bundle?) {
        getView()?.apply {
            getLifecycleOwner().lifecycleScope.launch(Dispatchers.IO) {
                IssueRepository.getInstance().getLatestIssueStub()?.let { issueStub ->
                    viewModel?.setIssueOperations(issueStub)
                }
            }
            // only show archive if created in the beginning else show current fragment
            if (savedInstanceState == null) {
                showDrawerFragment(SectionDrawerFragment())
            }
        }
    }
}
