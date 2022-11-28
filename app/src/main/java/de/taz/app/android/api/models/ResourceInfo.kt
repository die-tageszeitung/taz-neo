package de.taz.app.android.api.models

import android.content.Context
import de.taz.app.android.api.interfaces.DownloadableCollection
import de.taz.app.android.api.interfaces.ObservableDownload
import de.taz.app.android.persistence.repository.ResourceInfoRepository
import java.util.*

const val RESOURCE_FOLDER = "resources"
const val RESOURCE_TAG = "resources"


data class ResourceInfoKey(
    val minVersion: Int
): ObservableDownload {
    override fun getDownloadTag(): String {
        return RESOURCE_TAG
    }
}


data class ResourceInfo(
    val resourceVersion: Int,
    val resourceBaseUrl: String,
    val resourceZip: String,
    val resourceList: List<FileEntry>,
    override val dateDownload: Date?
) : DownloadableCollection {


    override suspend fun getDownloadDate(applicationContext: Context): Date? {
        return ResourceInfoRepository.getInstance(applicationContext).getDownloadStatus(this)
    }

    override suspend fun setDownloadDate(date: Date?, applicationContext: Context) {
        ResourceInfoRepository.getInstance(applicationContext).setDownloadStatus(this, date)
    }

    override suspend fun getAllFiles(): List<FileEntry> {
        return resourceList
    }

    override suspend fun getAllFileNames(): List<String> {
        return resourceList.map { it.name }
    }

    override fun getDownloadTag(): String {
        return RESOURCE_TAG
    }
}