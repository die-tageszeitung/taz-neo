package de.taz.app.android.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
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
import java.io.File
import java.io.FileOutputStream


@GlideModule
class ThumbnailAppModule : AppGlideModule() {
    override fun registerComponents(context: Context, glide: Glide, registry: Registry) {
        // register your Builder in Module
        // String.class is input and Bitmap.class is the output of ThumbnailBuilder
        registry.prepend(String::class.java, Bitmap::class.java, ThumbnailBuilderFactory(context))
    }
}

class ThumbnailBuilderFactory(
    /**
     * [Context] that pass to [PDFThumbnailLoader] class
     */
    private val context: Context
) :
    ModelLoaderFactory<String, Bitmap> {
    override fun build(multiFactory: MultiModelLoaderFactory): ModelLoader<String, Bitmap> {
        return PDFThumbnailLoader(context)
    }

    override fun teardown() = Unit
}

class PDFThumbnailLoader(private val context: Context): ModelLoader<String, Bitmap> {
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
                val output: Bitmap
                val photoCacheDir: File? = Glide.getPhotoCacheDir(context.applicationContext)
                val thumbnail = File(
                    photoCacheDir,
                    Uri.parse(input).lastPathSegment.toString() + ".png"
                )
                // check if file is already exist then there is no need to re create it
                if (!thumbnail.exists()) {
                    // create thumbnail for first page of pdf file
                    output = MuPDFThumbnail(input).thumbnail(width)
                    FileOutputStream(thumbnail).use { fos ->
                        output.compress(
                            Bitmap.CompressFormat.PNG,
                            100,
                            fos
                        )
                        fos.close()
                    }
                } else {
                    output = BitmapFactory.decodeFile(thumbnail.absolutePath)
                }
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
            return DataSource.MEMORY_CACHE
        }

    }

    override fun handles(model: String): Boolean {
        return model.endsWith(".pdf")
    }
}