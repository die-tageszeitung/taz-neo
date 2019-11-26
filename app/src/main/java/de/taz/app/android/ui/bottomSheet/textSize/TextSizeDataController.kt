package de.taz.app.android.ui.bottomSheet.textSize

import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.Observer
import de.taz.app.android.base.BaseDataController

class TextSizeDataController: BaseDataController(), TextSizeContract.DataController {

    // TODO use SharedPreferencesLiveData
    private val textSizeLiveData = MutableLiveData<Int>().apply { postValue(100) }

    // TODO use SharedPreferencesLiveData
    private val nightModeLiveData = MutableLiveData<Boolean>().apply { postValue(true) }

    override fun observeNightMode(lifecycleOwner: LifecycleOwner, block: (Boolean) -> Unit) {
        nightModeLiveData.observe(lifecycleOwner, Observer(block))
    }

    override fun observeTextSize(lifecycleOwner: LifecycleOwner, block: (Int) -> Unit) {
        textSizeLiveData.observe(lifecycleOwner, Observer(block))
    }

    override fun setNightMode(activated: Boolean) {
        nightModeLiveData.postValue(activated)
    }

    override fun setTextSizePercent(percent: Int) {
        textSizeLiveData.postValue(percent)
    }

    override fun getTextSizePercent(): Int {
        return textSizeLiveData.value ?: 100
    }
}