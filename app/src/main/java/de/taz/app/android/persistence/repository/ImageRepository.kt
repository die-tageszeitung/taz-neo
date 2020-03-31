package de.taz.app.android.persistence.repository

import android.content.Context
import de.taz.app.android.annotation.Mockable
import de.taz.app.android.api.dto.StorageType
import de.taz.app.android.api.models.Image
import de.taz.app.android.util.SingletonHolder

@Mockable
class ImageRepository private constructor(
    applicationContext: Context
) : RepositoryBase(applicationContext) {

    companion object : SingletonHolder<ImageRepository, Context>(::ImageRepository)

    fun save(image: Image) {
        val fromDB = appDatabase.imageDao().getByNameAndStorageType(image.name)
        fromDB?.let {
            if (fromDB.moTime < image.moTime)
                appDatabase.imageDao().insertOrReplace(image)
        } ?: appDatabase.imageDao().insertOrReplace(image)
    }

    fun save(fileEntries: List<Image>) {
        fileEntries.forEach { save(it) }
    }

    fun get(imageName: String, storageType: StorageType = StorageType.resource): Image? {
        return appDatabase.imageDao().getByNameAndStorageType(imageName, storageType)
    }

    fun get(imageNames: List<String>): List<Image> {
        return appDatabase.imageDao().getByNames(imageNames)
    }

    @Throws(NotFoundException::class)
    fun getOrThrow(imageName: String): Image {
        return get(imageName) ?: throw NotFoundException()
    }

    @Throws(NotFoundException::class)
    fun getOrThrow(imageNames: List<String>): List<Image> {
        return imageNames.map { getOrThrow(it) }
    }

    fun delete(image: Image) {
        appDatabase.downloadDao().apply {
            get(image.name)?.let {
                delete(it)
            }
        }
        appDatabase.imageDao().delete(image)
    }

    fun delete(fileEntries: List<Image>) {
        fileEntries.map { delete(it) }
    }

}