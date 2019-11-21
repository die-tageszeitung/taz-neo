package de.taz.app.android.ui.webview.pager

import android.os.Bundle
import androidx.lifecycle.Observer
import androidx.lifecycle.observe
import de.taz.app.android.api.models.Section
import de.taz.app.android.base.BasePresenter
import de.taz.app.android.util.Log

class SectionPagerPresenter : BasePresenter<SectionPagerContract.View, SectionPagerDataController>(
    SectionPagerDataController::class.java
), SectionPagerContract.Presenter {

    val log by Log

    override fun onViewCreated(savedInstanceState: Bundle?) {
        val localView = getView()
        val localViewModel = viewModel
        if (localViewModel != null && localView != null) {
            localViewModel.getSectionList().observe(localView.getLifecycleOwner()) { sections ->
                if (sections.isNotEmpty()) {
                    localView.setSections(sections, localViewModel.currentPosition)
                }
            }
            localViewModel.isLoading().observe(localView.getLifecycleOwner(), isLoadingObserver)
        }
    }

    private val isLoadingObserver = object : Observer<Boolean> {
        override fun onChanged(isLoading: Boolean) {
            log.debug("isLoadingChanges: $isLoading")
            if (!isLoading) {
                getView()?.onFinishedLoading()
                viewModel?.isLoading()?.removeObserver(this)
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
            showArchive()
        }
    }

    override fun onChildFragmentReady(section: Section) {
        log.debug("childFragmentReady ${section.sectionFileName}")
        viewModel?.setSectionLoaded(section)
    }
}