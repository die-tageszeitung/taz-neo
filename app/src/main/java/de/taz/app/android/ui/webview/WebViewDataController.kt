package de.taz.app.android.ui.webview

import androidx.annotation.VisibleForTesting
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.Observer
import de.taz.app.android.api.interfaces.WebViewDisplayable
import de.taz.app.android.base.BaseDataController

open class WebViewDataController<DISPLAYABLE: WebViewDisplayable> : BaseDataController(), WebViewContract.DataController<DISPLAYABLE> {

    @VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
    open val webViewDisplayable = MutableLiveData<DISPLAYABLE?>().apply {
        postValue(null)
    }

    fun observeWebViewDisplayable(lifecycleOwner: LifecycleOwner, block: (DISPLAYABLE?) -> Unit) {
        webViewDisplayable.observe(lifecycleOwner, Observer(block))
    }

    override fun getWebViewDisplayable(): DISPLAYABLE? {
        return webViewDisplayable.value
    }

    override fun setWebViewDisplayable(displayable: DISPLAYABLE?) {
        this.webViewDisplayable.postValue(displayable)
    }

}