package de.taz.app.android.ui.home.page.coverflow

import de.taz.app.android.ui.home.page.HomePageContract

interface CoverflowContract {

    interface View: HomePageContract.View {
        fun skipToEnd()
    }

    interface Presenter: HomePageContract.Presenter

}