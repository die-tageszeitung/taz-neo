package de.taz.app.android.ui.archive

import de.taz.app.android.base.BaseContract

interface ArchiveContract {

    interface View: BaseContract.View {

    }

    interface Presenter: BaseContract.Presenter {

        fun onRefresh()

        fun onScroll()

    }

    interface DataController {

        fun getLiveData()

    }

}