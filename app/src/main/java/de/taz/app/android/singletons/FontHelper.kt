package de.taz.app.android.singletons

import android.graphics.Typeface
import de.taz.app.android.api.models.RESOURCE_FOLDER
import de.taz.app.android.util.WoffConverter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

const val CONVERTED_FONT_FOLDER = "convertedFonts"

object FontHelper {

    private val fontFolder = FileHelper.getInstance().getFileByPath("$RESOURCE_FOLDER/$CONVERTED_FONT_FOLDER")
    private val cache: MutableMap<String, Typeface?> = mutableMapOf()

    init {
        if (!fontFolder.exists()) {
            fontFolder.mkdir()
        }
    }

    suspend fun getTypeFace(fileName: String): Typeface? {
        if (!cache.containsKey(fileName)) {
            cache[fileName] = fromFile(fileName)
        }
        return cache[fileName]
    }

    private suspend fun fromFile(fileName: String): Typeface? = withContext(Dispatchers.IO) {
        return@withContext FileHelper.getInstance().getFile(fileName)?.let {
            val ttfFile = File("${fontFolder}/${it.name.replace(".woff", ".ttf")}")
            if (!ttfFile.exists()) {
                ttfFile.createNewFile()
                ttfFile.writeBytes(
                    WoffConverter().convertToTTFByteArray(it.inputStream())
                )
            }
            Typeface.createFromFile(ttfFile)
        }
    }
}