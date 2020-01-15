package de.taz.app.android.ui.home.page.coverflow

import de.taz.app.android.ui.home.page.HomePageContract

interface CoverflowContract {

    interface View: HomePageContract.View {
        fun skipToEnd()
        fun skipToPosition(position: Int)
    }

    interface Presenter: HomePageContract.Presenter

}