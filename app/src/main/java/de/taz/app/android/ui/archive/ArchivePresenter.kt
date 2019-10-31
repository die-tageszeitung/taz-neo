package de.taz.app.android.ui.archive

import de.taz.app.android.base.BasePresenter

class ArchivePresenter : BasePresenter<ArchiveContract.View, ArchiveDataController>(
    ArchiveDataController::class.java
), ArchiveContract.Presenter {

    override fun onViewCreated() {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun onRefresh() {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun onScroll() {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }
}