package de.taz.app.android.ui.bottomSheet.textSize

import android.os.Bundle
import de.taz.app.android.base.BasePresenter
import de.taz.app.android.ui.archive.main.ArchiveFragment

const val MIN_TEXT_SIZE = 0
const val MAX_TEST_SIZE = 200

class TextSizePresenter : BasePresenter<TextSizeContract.View, TextSizeDataController>(
    TextSizeDataController::class.java
), TextSizeContract.Presenter {

    override fun onViewCreated(savedInstanceState: Bundle?) {
        getView()?.apply {
            viewModel?.observeNightMode(getLifecycleOwner()) { activated ->
                setNightMode(activated)
            }

            viewModel?.observeTextSize(getLifecycleOwner()) { textSizePercentage ->
                setTextSizePercentage(textSizePercentage)
            }

        }
    }

    override fun onNightModeChanged(activated: Boolean) {
        viewModel?.setNightMode(activated)
    }

    override fun onTextSizeChanged(percent: Int) {
        viewModel?.setTextSizePercent(percent)
    }

    override fun onSettingsSelected() {
        // TODO show correct fragment
        getView()?.getMainView()?.showMainFragment(ArchiveFragment())
    }

    override fun decreaseTextSize() {
        viewModel?.apply {
            val newSize = getTextSizePercent () - 10
            if (newSize >= MIN_TEXT_SIZE) {
                setTextSizePercent(newSize)
            }
        }
    }

    override fun increaseTextSize() {
        viewModel?.apply {
            val newSize = getTextSizePercent() + 10
            if (newSize <= MAX_TEST_SIZE) {
                setTextSizePercent(newSize)
            }
        }
    }

}