package de.taz.app.android.ui.webview.pager

import android.os.Bundle
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.observe
import de.taz.app.android.api.models.Section
import de.taz.app.android.base.BaseDataController
import de.taz.app.android.base.BasePresenter
import de.taz.app.android.util.Log

class ArticlePagerDataController : BaseDataController(),
    ArticlePagerContract.DataController {
    val section = MutableLiveData<Section>()
    var currentPosition = 0
}

class ArticlePagerPresenter: BasePresenter<ArticlePagerContract.View, ArticlePagerDataController>(
    ArticlePagerDataController::class.java
), ArticlePagerContract.Presenter {

    val log by Log

    override fun onViewCreated(savedInstanceState: Bundle?) {
        val localView = getView()
        val localViewModel = viewModel
        if (localViewModel != null && localView != null) {

            localViewModel.section.observe(localView.getLifecycleOwner()) { section ->

                log.debug("setSection: $section $localViewModel.currentPosition")
                localView.setSection(section, localViewModel.currentPosition)
            }

        }
    }

    override fun setSection(section: Section) {
        viewModel?.section?.postValue(section)
        log.debug("section = $section")
    }

    override fun setCurrrentPosition(position: Int) {
        viewModel?.currentPosition = position
        log.debug("currentPosition = $position")
    }

    override fun onBackPressed() {
        val localView = getView()
        val localViewModel = viewModel
        if (localView != null && localViewModel != null) {
            localViewModel.section.value?.let { localView.getMainView()?.showInWebView(it) }
        }
    }

}