package de.taz.app.android.ui.bottomSheet.textSize

import android.content.SharedPreferences
import androidx.lifecycle.LifecycleOwner
import de.taz.app.android.base.BaseContract

interface TextSizeContract {

    interface View: BaseContract.View {

        fun setTextSizePercentage(percent: String)

        fun setNightMode(active: Boolean)

    }

    interface Presenter: BaseContract.Presenter {

        fun onTextSizeChanged(percent: String)

        fun onNightModeChanged(activated: Boolean)

        fun onSettingsSelected()

        fun decreaseTextSize()

        fun increaseTextSize()
    }

    interface DataController: BaseContract.DataController {
        fun observeTextSize(lifecycleOwner: LifecycleOwner, block: (String) -> Unit)

        fun observeNightMode(lifecycleOwner: LifecycleOwner, block: (Boolean) -> Unit)

        fun setTextSizePercent(percent: String)

        fun getTextSizePercent(): String

        fun setNightMode(activated: Boolean)

        fun setPreferences(preferences: SharedPreferences)

    }

}