package de.taz.app.android.ui.webview.pager

import androidx.lifecycle.LiveData
import de.taz.app.android.api.models.Section
import de.taz.app.android.base.BaseContract

interface SectionPagerContract: BaseContract {
    interface View: BaseContract.View {
        fun setSections(sections: List<Section>, currentPosition: Int)
    }

    interface Presenter: BaseContract.Presenter {
        fun setInitialSection(section: Section)
        fun setCurrrentPosition(position: Int)
        fun onBackPressed()
    }

    interface DataController: BaseContract.DataController {
        var currentPosition: Int
        fun setInitialSection(section: Section)
        fun getSectionList(): LiveData<List<Section>>
    }
}