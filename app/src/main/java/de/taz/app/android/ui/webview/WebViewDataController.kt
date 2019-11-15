package de.taz.app.android.ui.webview

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.Transformations
import de.taz.app.android.api.interfaces.WebViewDisplayable
import de.taz.app.android.base.BaseDataController
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import java.io.File

open class WebViewDataController<DISPLAYABLE: WebViewDisplayable> : BaseDataController(), WebViewContract.DataController<DISPLAYABLE> {

    private val webViewDisplayable = MutableLiveData<DISPLAYABLE?>().apply {
        postValue(null)
    }

    val fileLiveData: LiveData<File?> = Transformations.map(webViewDisplayable) {
        runBlocking(Dispatchers.IO) {
            it?.getFile()
        }
    }

    override fun getWebViewDisplayable(): DISPLAYABLE? {
        return webViewDisplayable.value
    }

    override fun setWebViewDisplayable(displayable: DISPLAYABLE?) {
        this.webViewDisplayable.postValue(displayable)
    }

}