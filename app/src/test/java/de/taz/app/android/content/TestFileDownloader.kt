package de.taz.app.android.content

import de.taz.app.android.content.cache.*
import de.taz.app.android.download.FiledownloaderInterface
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

abstract class TestFileDownloader : FiledownloaderInterface {

    abstract suspend fun fakeDownloadItem(item: CacheOperationItem<FileCacheItem>)

    override suspend fun enqueueDownload(operation: ContentDownload) {
        for (item in operation.cacheItems) {
            CoroutineScope(Dispatchers.IO).launch {
                fakeDownloadItem(item)
                operation.checkIfItemsCompleteAndNotifyResult(Unit)
            }
        }
    }
}