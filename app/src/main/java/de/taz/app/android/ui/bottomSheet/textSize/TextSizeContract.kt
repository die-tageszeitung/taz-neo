package de.taz.app.android.ui.bottomSheet.textSize

import androidx.lifecycle.LifecycleOwner
import de.taz.app.android.base.BaseContract

interface TextSizeContract {

    interface View: BaseContract.View {

        fun setTextSizePercentage(percent: Int)

        fun setNightMode(active: Boolean)

    }

    interface Presenter: BaseContract.Presenter {

        fun onTextSizeChanged(percent: Int)

        fun onNightModeChanged(activated: Boolean)

        fun onSettingsSelected()

        fun decreaseTextSize()

        fun increaseTextSize()
    }

    interface DataController: BaseContract.DataController {
        fun observeTextSize(lifecycleOwner: LifecycleOwner, block: (Int) -> Unit)

        fun observeNightMode(lifecycleOwner: LifecycleOwner, block: (Boolean) -> Unit)

        fun setTextSizePercent(percent: Int)

        fun getTextSizePercent(): Int

        fun setNightMode(activated: Boolean)

    }

}