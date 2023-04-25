package de.taz.app.android.singletons

import android.content.Context
import android.graphics.Typeface
import androidx.lifecycle.ViewModel
import de.taz.app.android.api.models.RESOURCE_FOLDER
import de.taz.app.android.util.SingletonHolder
import de.taz.app.android.util.WoffConverter
import io.sentry.Sentry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileNotFoundException
import java.lang.RuntimeException

const val CONVERTED_FONT_FOLDER = "convertedFonts"

class FontHelper private constructor(applicationContext: Context) : ViewModel() {

    companion object : SingletonHolder<FontHelper, Context>(::FontHelper)

    private val storageService = StorageService.getInstance(applicationContext)
    private var fontFolder: File? = null

    private val cache: MutableMap<String, Typeface?> = mutableMapOf()
    private val mutex = Mutex()

    suspend fun getTypeFace(file: File): Typeface? {
        return if (cache.containsKey(file.name)) {
            cache[file.name]
        } else {
            mutex.withLock {
                if (!cache.containsKey(file.name)) {
                    cache[file.name] = fromFile(file)
                }
                cache[file.name]
            }
        }
    }

    private suspend fun ensureFontFolderExists(): Unit = withContext(Dispatchers.IO) {
        fontFolder = fontFolder
            ?: File(
                "${storageService.getInternalFilesDir().absolutePath}/$RESOURCE_FOLDER/$CONVERTED_FONT_FOLDER"
            ).also {
                if (!it.exists()) {
                    it.mkdir()
                }
            }
    }

    private suspend fun fromFile(file: File): Typeface? = withContext(Dispatchers.IO) {
        try {
            ensureFontFolderExists()
            file.inputStream().use {
                val ttfFile = File("${fontFolder}/${file.name.replace(".woff", ".ttf")}")
                if (!ttfFile.exists()) {
                    ttfFile.writeBytes(
                        WoffConverter().convertToTTFByteArray(it)
                    )
                }
                try {
                    Typeface.createFromFile(ttfFile)
                } catch (re: RuntimeException) {
                    ttfFile.delete()
                    fromFile(file)
                }
            }
        } catch (e: FileNotFoundException) {
            log.warn("Accessing ${file.name} threw $e", e)
            Sentry.captureException(e)
            null
        }
    }
}