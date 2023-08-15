package de.taz.app.android.util

import android.content.Context
import android.graphics.Bitmap
import com.bumptech.glide.Glide
import com.bumptech.glide.Priority
import com.bumptech.glide.Registry
import com.bumptech.glide.annotation.GlideModule
import com.bumptech.glide.load.DataSource
import com.bumptech.glide.load.Options
import com.bumptech.glide.load.data.DataFetcher
import com.bumptech.glide.load.model.ModelLoader
import com.bumptech.glide.load.model.ModelLoaderFactory
import com.bumptech.glide.load.model.MultiModelLoaderFactory
import com.bumptech.glide.module.AppGlideModule
import com.bumptech.glide.signature.ObjectKey
import de.taz.app.android.ui.pdfViewer.MuPDFThumbnail
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException


@GlideModule
class ThumbnailAppModule : AppGlideModule() {
    override fun registerComponents(context: Context, glide: Glide, registry: Registry) {
        // register your Builder in Module
        // String.class is input and Bitmap.class is the output of ThumbnailBuilder
        registry.prepend(String::class.java, Bitmap::class.java, ThumbnailBuilderFactory())
    }
}


class ThumbnailBuilderFactory: ModelLoaderFactory<String, Bitmap> {
    override fun build(multiFactory: MultiModelLoaderFactory): ModelLoader<String, Bitmap> {
        return PDFThumbnailLoader()
    }

    override fun teardown() = Unit
}

class PDFThumbnailLoader: ModelLoader<String, Bitmap> {
    override fun buildLoadData(
        model: String,
        width: Int,
        height: Int,
        options: Options
    ): ModelLoader.LoadData<Bitmap> {
        return ModelLoader.LoadData(ObjectKey(model), PDFThumbnailCreator(model, width))
    }

    private inner class PDFThumbnailCreator(
        private val input: String,
        private val width: Int
    ): DataFetcher<Bitmap> {
        override fun loadData(priority: Priority, callback: DataFetcher.DataCallback<in Bitmap>) {
            try {
                // create thumbnail for first page of pdf file
                val output: Bitmap = MuPDFThumbnail(input).thumbnail(width)
                // send output data
                callback.onDataReady(output)
            } catch (e: Exception) {
                // if error
                callback.onLoadFailed(e)
            }
        }

        override fun cleanup() = Unit

        override fun cancel() = Unit

        override fun getDataClass(): Class<Bitmap> {
            return Bitmap::class.java
        }

        override fun getDataSource(): DataSource {
            return DataSource.LOCAL
        }

    }

    override fun handles(model: String): Boolean {
        return model.endsWith(".pdf")
    }
}

/**
 * Until 1.7.3 the PDFThumbnailLoader created a custom cache for each thumbnailed pdf and stored it
 * forever in the Glide cache directory.
 * As this functionality is no longer used, this helper can be used to delete these cache items.
 */
suspend fun clearCustomPDFThumbnailLoaderCache(context: Context) = withContext(Dispatchers.IO) {
    val photoCacheDir: File? = Glide.getPhotoCacheDir(context.applicationContext)
    photoCacheDir
        ?.listFiles { _, name -> name.endsWith(".pdf.png")}
        ?.forEach { file ->
            try {
                file.delete()
            } catch (ioException: IOException) {
                // Ignore errors
            }
        }
}