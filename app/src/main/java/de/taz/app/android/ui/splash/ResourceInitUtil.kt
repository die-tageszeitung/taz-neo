package de.taz.app.android.ui.splash

import android.content.Context
import de.taz.app.android.R
import de.taz.app.android.TAZ_API_CSS_FILENAME
import de.taz.app.android.TAZ_API_JS_FILENAME
import de.taz.app.android.api.interfaces.StorageLocation
import de.taz.app.android.api.models.FileEntry
import de.taz.app.android.api.models.ImageResolution
import de.taz.app.android.api.models.ImageStub
import de.taz.app.android.api.models.ImageType
import de.taz.app.android.api.models.RESOURCE_FOLDER
import de.taz.app.android.api.models.StorageType
import de.taz.app.android.dataStore.StorageDataStore
import de.taz.app.android.persistence.repository.FileEntryRepository
import de.taz.app.android.persistence.repository.ImageRepository
import de.taz.app.android.singletons.ExternalStorageNotAvailableException
import de.taz.app.android.singletons.NightModeHelper
import de.taz.app.android.singletons.StorageService
import de.taz.app.android.util.Log
import java.io.File
import java.util.Date

/**
 * Util class wrapping function to initialize resource files required by the app.
 * This class is not intended to be used as a Singleton - the applicationContext in the
 * constructor is merely passed for convenience when using the individual init functions.
 */
class ResourceInitUtil(private val applicationContext: Context) {
    private val log by Log

    private val storageDataStore = StorageDataStore.getInstance(applicationContext)
    private val fileEntryRepository = FileEntryRepository.getInstance(applicationContext)
    private val storageService = StorageService.getInstance(applicationContext)
    private val imageRepository = ImageRepository.getInstance(applicationContext)
    private val nightModeHelper = NightModeHelper.getInstance(applicationContext)

    private fun ensureResourceFolderExists(storageLocation: StorageLocation) {
        val resourcePath =
            requireNotNull(storageService.getAbsolutePath(RESOURCE_FOLDER, storageLocation))
        File(resourcePath).mkdirs()
    }

    suspend fun ensureTazApiJsExists() {
        val existingTazApiJSFileEntry = fileEntryRepository.get(TAZ_API_JS_FILENAME)
        val currentStorageLocation = storageDataStore.storageLocation.get()
        ensureResourceFolderExists(currentStorageLocation)

        var tazApiJSFileEntry =
            if (existingTazApiJSFileEntry != null && existingTazApiJSFileEntry.storageLocation != StorageLocation.NOT_STORED) {
                existingTazApiJSFileEntry
            } else {
                val newFileEntry = FileEntry(
                    name = TAZ_API_JS_FILENAME,
                    storageType = StorageType.resource,
                    moTime = Date().time,
                    sha256 = "",
                    size = 0,
                    folder = RESOURCE_FOLDER,
                    dateDownload = null,
                    path = "$RESOURCE_FOLDER/$TAZ_API_JS_FILENAME",
                    storageLocation = currentStorageLocation
                )
                val newFile = storageService.getFile(newFileEntry)
                if (newFile?.exists() == true) {
                    fileEntryRepository.saveOrReplace(
                        newFileEntry.copy(
                            dateDownload = Date(),
                            size = newFile.length(),
                            sha256 = storageService.getSHA256(newFile)
                        )
                    )
                } else {
                    fileEntryRepository.saveOrReplace(newFileEntry)
                }
            }
        val tazApiAssetPath = "js/tazApi.js"
        val tazApiJSFile = try {
            storageService.getFile(tazApiJSFileEntry)
        } catch (e: ExternalStorageNotAvailableException) {
            // If card was ejected create new file
            tazApiJSFileEntry =
                fileEntryRepository.saveOrReplace(tazApiJSFileEntry.copy(storageLocation = StorageLocation.INTERNAL))
            storageService.getFile(tazApiJSFileEntry)
        }
        if (tazApiJSFile?.exists() == false || (tazApiJSFile != null && !storageService.assetFileSameContentAsFile(
                tazApiAssetPath, tazApiJSFile
            ))
        ) {
            storageService.copyAssetFileToFile(tazApiAssetPath, tazApiJSFile)
            fileEntryRepository.saveOrReplace(
                tazApiJSFileEntry.copy(
                    dateDownload = Date(),
                    size = tazApiJSFile.length(),
                    sha256 = storageService.getSHA256(tazApiJSFile)
                )
            )
            log.verbose("Created/updated $TAZ_API_JS_FILENAME")
        }
    }

    suspend fun ensureTazApiCssExists() {
        val existingTazApiCSSFileEntry = fileEntryRepository.get(TAZ_API_CSS_FILENAME)
        val currentStorageLocation = storageDataStore.storageLocation.get()
        ensureResourceFolderExists(currentStorageLocation)

        var tazApiCSSFileEntry =
            if (existingTazApiCSSFileEntry != null && existingTazApiCSSFileEntry.storageLocation != StorageLocation.NOT_STORED) {
                existingTazApiCSSFileEntry
            } else {
                val newFileEntry = FileEntry(
                    name = TAZ_API_CSS_FILENAME,
                    storageType = StorageType.resource,
                    moTime = Date().time,
                    sha256 = "",
                    size = 0,
                    folder = RESOURCE_FOLDER,
                    dateDownload = null,
                    path = "$RESOURCE_FOLDER/$TAZ_API_CSS_FILENAME",
                    storageLocation = currentStorageLocation
                )
                val newFile = storageService.getFile(newFileEntry)
                if (newFile?.exists() == true) {
                    fileEntryRepository.saveOrReplace(
                        newFileEntry.copy(
                            dateDownload = Date(),
                            size = newFile.length(),
                            sha256 = storageService.getSHA256(newFile)
                        )
                    )
                } else {
                    fileEntryRepository.saveOrReplace(newFileEntry)
                }
            }

        val tazApiCSSFile = try {
            storageService.getFile(tazApiCSSFileEntry)
        } catch (e: ExternalStorageNotAvailableException) {
            // If card was ejected create new file
            tazApiCSSFileEntry =
                fileEntryRepository.saveOrReplace(tazApiCSSFileEntry.copy(storageLocation = StorageLocation.INTERNAL))
            storageService.getFile(tazApiCSSFileEntry)
        }
        if (tazApiCSSFile?.exists() == false) {
            tazApiCSSFile.createNewFile()
            fileEntryRepository.saveOrReplace(
                tazApiCSSFileEntry.copy(
                    dateDownload = Date(),
                    size = tazApiCSSFile.length(),
                    sha256 = storageService.getSHA256(tazApiCSSFile),
                )
            )
            nightModeHelper.notifyTazApiCSSFileReady()
            log.verbose("Created $TAZ_API_CSS_FILENAME")
        }
    }

    suspend fun ensureDefaultNavButtonExists() {
        val defaultNavButtonFileName =
            applicationContext.getString(R.string.DEFAULT_NAV_DRAWER_FILE_NAME)
        val currentStorageLocation = storageDataStore.storageLocation.get()
        ensureResourceFolderExists(currentStorageLocation)

        val existingNavButtonFileEntry = fileEntryRepository.get(defaultNavButtonFileName)
        if (existingNavButtonFileEntry != null && existingNavButtonFileEntry.storageLocation != StorageLocation.NOT_STORED) {
            try {
                storageService.getFile(existingNavButtonFileEntry)
            } catch (e: ExternalStorageNotAvailableException) {
                // If card was ejected we can not copy the previous file over.
                log.warn("External storage is not available. Ensure a nav button exists by re-creating the default one.")
                // But have to ensure that a navButton exists. Thus we re-create the default nav button on the current storage.
                createDefaultNavButton(defaultNavButtonFileName, currentStorageLocation)
            }
        } else if (existingNavButtonFileEntry != null && existingNavButtonFileEntry.storageLocation == StorageLocation.NOT_STORED) {
            log.warn("A nav button FilEntry exists, but there is no file stored yet. Re-create the default nav button.")
            createDefaultNavButton(defaultNavButtonFileName, currentStorageLocation)
        } else {
            createDefaultNavButton(defaultNavButtonFileName, currentStorageLocation)
        }
    }

    private suspend fun createDefaultNavButton(fileName: String, storageLocation: StorageLocation) {
        log.verbose("Create default nav button from assets")
        val defaultNavButtonAssetPath = "img/defaultNavButton.png"

        // Create a dummy file entry (not stored to the DB) that is used to get a File handle of the actual destination path
        val destDummyFileEntry = FileEntry(
            name = fileName,
            storageType = StorageType.resource,
            moTime = 0L, // ensure the file will be overwritten by the latest resources from the server
            sha256 = "",
            size = 0,
            folder = RESOURCE_FOLDER,
            dateDownload = null,
            path = "$RESOURCE_FOLDER/$fileName",
            storageLocation = storageLocation
        )

        val destFile = storageService.getFile(destDummyFileEntry)
            ?: error("Could not create the File handle for the default nav button path.")

        if (destFile.exists()) {
            log.warn("Existing nav button at ${destFile.path} will be overwritten with the default asset $defaultNavButtonAssetPath")
        }

        storageService.copyAssetFileToFile(defaultNavButtonAssetPath, destFile)

        // Create the actual fileEntry to be stored to the DB.
        // moTime must remain 0, as we want to ensure that the latest nav button from the resources takes precedence
        val fileEntry = destDummyFileEntry.copy(
            size = destFile.length(),
            sha256 = storageService.getSHA256(destFile),
            dateDownload = Date()
        )
        fileEntryRepository.saveOrReplace(fileEntry)

        // Save the navButton Image
        val imageStub = ImageStub(fileName, ImageType.button, 1f, ImageResolution.normal)
        imageRepository.saveOrReplace(imageStub)
    }

}