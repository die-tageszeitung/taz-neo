package de.taz.app.android.singletons

import android.content.Context
import android.graphics.Typeface
import androidx.lifecycle.ViewModel
import de.taz.app.android.api.models.RESOURCE_FOLDER
import de.taz.app.android.util.SingletonHolder
import de.taz.app.android.util.WoffConverter
import io.sentry.core.Sentry
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

    private val fileHelper = FileHelper.getInstance(applicationContext)
    private val fontFolder = fileHelper.getFileByPath("$RESOURCE_FOLDER/$CONVERTED_FONT_FOLDER")

    private val cache: MutableMap<String, Typeface?> = mutableMapOf()
    private val mutex = Mutex()

    init {
        if (!fontFolder.exists()) {
            fontFolder.mkdir()
        }
    }

    suspend fun getTypeFace(fileName: String): Typeface? {
        return if (cache.containsKey(fileName)) {
            cache[fileName]
        } else {
            mutex.withLock {
                if (!cache.containsKey(fileName)) {
                    cache[fileName] = fromFile(fileName)
                }
                cache[fileName]
            }
        }
    }

    private suspend fun fromFile(fileName: String): Typeface? = withContext(Dispatchers.IO) {
        val woffFile = fileHelper.getFile(fileName)
        try {
            woffFile?.inputStream()?.use {
                val ttfFile = File("${fontFolder}/${fileName.replace(".woff", ".ttf")}")
                if (!ttfFile.exists()) {
                    ttfFile.writeBytes(
                        WoffConverter().convertToTTFByteArray(it)
                    )
                }
                try {
                    Typeface.createFromFile(ttfFile)
                } catch (re: RuntimeException) {
                    ttfFile.delete()
                    fromFile(fileName)
                }
            }
        } catch (e: FileNotFoundException) {
            val hint = "Accessing $fileName threw $e"
            Sentry.captureException(e, hint)
            null
        }
    }
}