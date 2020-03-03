package de.taz.app.android.ui.webview

import androidx.annotation.VisibleForTesting
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.MutableLiveData
import de.taz.app.android.annotation.Mockable
import de.taz.app.android.api.interfaces.WebViewDisplayable
import de.taz.app.android.base.BaseDataController
import de.taz.app.android.monkey.observeDistinct

@Mockable
class WebViewDataController<DISPLAYABLE : WebViewDisplayable> : BaseDataController(),
    WebViewContract.DataController<DISPLAYABLE> {

    @VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
    val webViewDisplayable = MutableLiveData<DISPLAYABLE?>().apply {
        postValue(null)
    }

    fun observeWebViewDisplayable(
        lifecycleOwner: LifecycleOwner,
        observationCallback: (DISPLAYABLE?) -> Unit
    ) {
        webViewDisplayable.observeDistinct(lifecycleOwner, observationCallback)
    }

    override fun getWebViewDisplayable(): DISPLAYABLE? {
        return webViewDisplayable.value
    }

    override fun setWebViewDisplayable(displayable: DISPLAYABLE?) {
        this.webViewDisplayable.postValue(displayable)
    }

}