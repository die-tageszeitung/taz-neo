package de.taz.app.android.api.models

import android.content.Context
import de.taz.app.android.api.dto.ProductDto
import de.taz.app.android.persistence.AppDatabase
import de.taz.app.android.persistence.entities.FileEntryEntity
import de.taz.app.android.persistence.entities.ResourceInfoEntity
import de.taz.app.android.persistence.entities.ResourceInfoFileEntryJoin

open class ResourceInfo(
    open val resourceVersion: Int,
    open val resourceBaseUrl: String,
    open val resourceZip: String,
    open val resourceList: List<FileEntry>
) {
    constructor(productDto: ProductDto) : this (
        productDto.resourceVersion!!,
        productDto.resourceBaseUrl!!,
        productDto.resourceZip!!,
        productDto.resourceList!!
    )

    fun save(context: Context? = null) {
        val appDatabase = AppDatabase.getInstance(context)
        appDatabase.resourceInfoDao().insertOrReplace(
            ResourceInfoEntity(resourceVersion, resourceBaseUrl, resourceZip)
        )
        appDatabase.fileEntryDao().insertOrReplace(resourceList.map { FileEntryEntity(it) })
        appDatabase.resourceInfoFileEntryJoinDao().insertOrReplace(resourceList.map { ResourceInfoFileEntryJoin(resourceVersion, it.name) })
    }

    companion object {
        fun get(context: Context?): ResourceInfo {
            return AppDatabase.getInstance(context).resourceInfoDao().get().toObject()
        }
    }
}