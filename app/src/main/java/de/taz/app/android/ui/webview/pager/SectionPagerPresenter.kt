package de.taz.app.android.ui.webview.pager

import android.os.Bundle
import androidx.lifecycle.observe
import de.taz.app.android.api.models.Section
import de.taz.app.android.base.BasePresenter

class SectionPagerPresenter : BasePresenter<SectionPagerContract.View, SectionPagerDataController>(
    SectionPagerDataController::class.java
), SectionPagerContract.Presenter {

    override fun onViewCreated(savedInstanceState: Bundle?) {
        val localView = getView()
        val localViewModel = viewModel
        if (localViewModel != null && localView != null) {
            localViewModel.getSectionList().observe(localView.getLifecycleOwner()) { sections ->
                if (sections.isNotEmpty()) {
                    localView.setSections(sections, localViewModel.currentPosition)
                }
            }
        }
    }

    override fun setInitialSection(section: Section) {
        viewModel?.setInitialSection(section)
    }

    override fun setCurrentPosition(position: Int) {
        viewModel?.currentPosition = position
    }

    override fun onBackPressed() {
        getView()?.getMainView()?.apply {
            showFeed()
        }
    }

    override fun trySetSection(section: Section): Boolean {
        val localView = getView()
        val localViewModel = viewModel
        if (localViewModel != null && localView != null) {
            if (localViewModel.trySetSection(section)) {
                localView.setCurrentPosition(localViewModel.currentPosition)
                return true
            }
        }
        return false
    }
}