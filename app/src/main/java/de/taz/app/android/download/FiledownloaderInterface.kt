package de.taz.app.android.download
import de.taz.app.android.content.cache.ContentDownload

interface FiledownloaderInterface {
    suspend fun enqueueDownload(operation: ContentDownload)
}
