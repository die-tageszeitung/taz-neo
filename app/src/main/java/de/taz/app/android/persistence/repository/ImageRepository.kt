package de.taz.app.android.persistence.repository

import android.content.Context
import de.taz.app.android.api.models.Article
import de.taz.app.android.api.models.FileEntry
import de.taz.app.android.api.models.Image
import de.taz.app.android.api.models.ImageStub
import de.taz.app.android.util.SingletonHolder


class ImageRepository private constructor(
    val applicationContext: Context
) : RepositoryBase(applicationContext) {

    companion object : SingletonHolder<ImageRepository, Context>(::ImageRepository)

    private val fileEntryRepository = FileEntryRepository.getInstance(applicationContext)

    /**
     * Save the [Image] to the database and replace any existing [Image] with the same key.
     * The related [FileEntry] is saved recursively and replaces any existing [FileEntry] with the
     * same key but an earlier modification time.
     *
     * This method must be called as part of a transaction, for example when saving an [Article].
     */
    suspend fun saveInternal(image: Image) {
        appDatabase.imageStubDao().insertOrReplace(ImageStub(image))
        fileEntryRepository.save(FileEntry(image))
    }

    /**
     * Save the list of [Image]s to the database and replace any existing [Image] with the same key.
     * The related [FileEntry]s are saved recursively.
     *
     * This method must be called as part of a transaction, for example when saving an [Article].
     */
    suspend fun saveInternal(images: List<Image>) {
        images.forEach { saveInternal(it) }
    }

    /**
     * Save the [ImageStub] to the database and replace any existing [ImageStub] with the same key.
     * It does not save the related [FileEntry].
     */
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