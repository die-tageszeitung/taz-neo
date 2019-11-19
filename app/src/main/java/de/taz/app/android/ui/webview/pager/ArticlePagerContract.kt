package de.taz.app.android.ui.webview.pager

import de.taz.app.android.api.models.Section
import de.taz.app.android.base.BaseContract

interface ArticlePagerContract: BaseContract {
    interface View: BaseContract.View {
        fun setSection(section: Section, currentPosition: Int)
    }

    interface Presenter: BaseContract.Presenter {
        fun setSection(section: Section)
        fun setCurrrentPosition(position: Int)
        fun onBackPressed()
    }

    interface DataController: BaseContract.DataController
}