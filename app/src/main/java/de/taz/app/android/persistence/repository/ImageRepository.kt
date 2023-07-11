package de.taz.app.android.persistence.repository

import android.content.Context
import de.taz.app.android.annotation.Mockable
import de.taz.app.android.api.models.FileEntry
import de.taz.app.android.api.models.Image
import de.taz.app.android.api.models.ImageStub
import de.taz.app.android.util.SingletonHolder

@Mockable
class ImageRepository private constructor(
    val applicationContext: Context
) : RepositoryBase(applicationContext) {

    companion object : SingletonHolder<ImageRepository, Context>(::ImageRepository)

    suspend fun save(image: Image) {
        appDatabase.imageStubDao().insertOrReplace(ImageStub(image))
        FileEntryRepository.getInstance(applicationContext).save(FileEntry(image))
    }

    suspend fun save(images: List<Image>) {
        images.forEach { save(it) }
    }

    suspend fun saveOrReplace(imageStub: ImageStub) {
        appDatabase.imageStubDao().insertOrReplace(imageStub)
    }

    suspend fun get(imageName: String): Image? {
        return appDatabase.imageDao().getByName(imageName)
    }

    suspend fun getStub(imageName: String): ImageStub? {
        return appDatabase.imageStubDao().getByName(imageName)
    }

    suspend fun get(imageNames: List<String>): List<Image> {
        return appDatabase.imageDao().getByNames(imageNames)
    }

    suspend fun delete(image: Image) {
        FileEntryRepository.getInstance(applicationContext).apply {
            get(image.name)?.let { delete(it) }
        }
        appDatabase.imageStubDao().delete(ImageStub(image))
    }

    suspend fun delete(fileEntries: List<Image>) {
        fileEntries.map { delete(it) }
    }

}