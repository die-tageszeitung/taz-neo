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
    applicationContext: Context
) : RepositoryBase(applicationContext) {

    companion object : SingletonHolder<ImageRepository, Context>(::ImageRepository)

    fun update(image: Image) {
        appDatabase.imageStubDao().update(ImageStub(image))
        appDatabase.fileEntryDao().update(FileEntry(image))
    }

    fun update(images: List<Image>) {
        appDatabase.imageStubDao().update(images.map { ImageStub(it) })
        appDatabase.fileEntryDao().update(images.map { FileEntry(it) })
    }

    fun save(image: Image) {
        appDatabase.imageStubDao().insertOrReplace(ImageStub(image))
        appDatabase.fileEntryDao().insertOrReplace(FileEntry(image))
    }

    fun save(images: List<Image>) {
        images.forEach { save(it) }
    }

    fun saveStub(imageStub: ImageStub) {
        appDatabase.imageStubDao().insertOrReplace(imageStub)
    }

    fun get(imageName: String): Image? {
        return appDatabase.imageDao().getByName(imageName)
    }

    fun getLiveData(imageName: String): LiveData<Image?> {
        return appDatabase.imageDao().getLiveDataByName(imageName)
    }

    fun returnExisting(imageNames: List<String>): List<String> {
        return appDatabase.imageDao().getNames(imageNames)
    }

    fun getStub(imageName: String): ImageStub? {
        return appDatabase.imageStubDao().getByName(imageName)
    }

    fun get(imageNames: List<String>): List<Image> {
        return appDatabase.imageDao().getByNames(imageNames)
    }

    fun getStubs(imageNames: List<String>): List<ImageStub> {
        return appDatabase.imageStubDao().getByNames(imageNames)
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
        val fileEntry = FileEntry(image)
        appDatabase.fileEntryDao().delete(fileEntry)
        fileEntry.deleteFile()
        appDatabase.imageStubDao().delete(ImageStub(image))
    }

    fun delete(fileEntries: List<Image>) {
        fileEntries.map { delete(it) }
    }

}