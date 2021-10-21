package de.taz.app.android.persistence.repository

import android.content.Context
import androidx.lifecycle.LiveData
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

    fun save(image: Image) {
        appDatabase.imageStubDao().insertOrReplace(ImageStub(image))
        FileEntryRepository.getInstance(applicationContext).save(FileEntry(image))
    }

    fun save(images: List<Image>) {
        images.forEach { save(it) }
    }

    fun get(imageName: String): Image? {
        return appDatabase.imageDao().getByName(imageName)
    }

    fun getLiveData(imageName: String): LiveData<Image?> {
        return appDatabase.imageDao().getLiveDataByName(imageName)
    }

    fun getStub(imageName: String): ImageStub? {
        return appDatabase.imageStubDao().getByName(imageName)
    }

    fun get(imageNames: List<String>): List<Image> {
        return appDatabase.imageDao().getByNames(imageNames)
    }

    fun delete(image: Image) {
        FileEntryRepository.getInstance(applicationContext).apply {
            get(image.name)?.let { delete(it) }
        }
        appDatabase.imageStubDao().delete(ImageStub(image))
    }

    fun delete(fileEntries: List<Image>) {
        fileEntries.map { delete(it) }
    }

}