package de.taz.app.android.ui.pdfViewer.mupdf

import android.content.Context
import android.graphics.*
import android.os.Build
import android.os.Handler
import android.view.ViewGroup
import android.widget.ImageView.ScaleType
import android.widget.ProgressBar
import androidx.appcompat.widget.AppCompatImageView
import com.artifex.mupdf.fitz.Cookie
import com.artifex.mupdf.fitz.FileStream
import de.taz.app.android.api.models.Page
import de.taz.app.android.api.models.PageType
import de.taz.app.android.singletons.StorageService
import de.taz.app.android.util.Log
import de.taz.app.android.util.runIfNotNull
import kotlin.math.min

class PageView(
    context: Context,
    private val parentSize: Point,
    sharedHqBm: Bitmap,
) : ViewGroup(
    context
) {
    companion object {
        private const val BACKGROUND_COLOR = -0x1
        private const val PROGRESS_DIALOG_DELAY = 200
        private const val MIME_TYPE_PDF = "application/pdf"
    }

    private val log by Log
    private val storageService: StorageService =
        StorageService.getInstance(context.applicationContext)

    var page: Page? = null
        private set
    private var muPDFCore: MuPDFCore? = null

    // Size of page at minimum zoom
    var minZoomSize: Point = parentSize
        private set
    var sourceScale = 0f
        private set

    // Image rendered at minimum zoom
    private val entire: AppCompatImageView = OpaqueImageView(context).apply {
        scaleType = ScaleType.MATRIX
    }
    private var entireBm: Bitmap? = null
    private val entireMat: Matrix = Matrix()
    private var drawEntire: CancellableAsyncTask<Void?, Void?>? = null

    // View size on the basis of which the patch was created
    private var patchViewSize: Point? = null
    private var patchArea: Rect? = null
    private val patch: AppCompatImageView = OpaqueImageView(context).apply {
        scaleType = ScaleType.MATRIX
    }
    private val patchBm: Bitmap = sharedHqBm
    private var drawPatch: CancellableAsyncTask<Void?, Void?>? = null

    private var isBlank = false
    private var busyIndicator: ProgressBar? = null

    private val _handler = Handler()

    var scale: Float = 1f

    init {
        setBackgroundColor(BACKGROUND_COLOR)

        addView(entire)
        addView(patch)
    }

    private fun reinit() {
        // Cancel pending render task
        cancelDrawEntireTask()
        cancelDrawPatchTask()

        isBlank = true

        page = null
        muPDFCore?.onDestroy()
        muPDFCore = null

        clearEntire()
        clearPatch()
    }

    private fun clearPatch() {
        patchViewSize = null
        patchArea = null
        patch.apply {
            setImageBitmap(null)
            invalidate()
        }
    }

    private fun clearEntire() {
        entire.apply {
            setImageBitmap(null)
            invalidate()
        }
    }

    private fun cancelDrawEntireTask() {
        drawEntire?.apply {
            cancel()
            drawEntire = null
        }
    }

    private fun cancelDrawPatchTask() {
        drawPatch?.apply {
            cancel()
            drawPatch = null
        }
    }

    fun releaseResources() {
        reinit()
        removeBusyIndicator()
    }

    private fun removeBusyIndicator() {
        busyIndicator?.let {
            removeView(it)
            busyIndicator = null
        }
    }

    fun blank() {
        reinit()
        if (busyIndicator == null) {
            busyIndicator = ProgressBar(context).apply {
                isIndeterminate = true
                this@PageView.addView(this)
            }

        }
        setBackgroundColor(BACKGROUND_COLOR)
    }


    // Returns true if this was a new page
    private fun setPage(page: Page): Boolean {
        // Always reset the scale, even if this is the same page again
        scale = 1f
        return if (this.page == page) {
            false
        } else {
            this.page = page
            val pdfFilePath = requireNotNull(storageService.getAbsolutePath(page.pagePdf))
            val fileStream = FileStream(pdfFilePath, "r")
            muPDFCore = MuPDFCore(fileStream, MIME_TYPE_PDF)
            true
        }
    }

    fun setPage(page: Page, onPageSizeCalculated: (PointF) -> Unit) {
        if (!setPage(page)) {
            return
        }

        // FIXME (johannes): We dont have to blank, as we will calculate the page  size immediately
        //                   once we do get it async from a thread, we might want to blank
        // blank()

        val muPDFCore = requireNotNull(this.muPDFCore)
        val pageSize = muPDFCore.getPageSize(0)
        drawPage(pageSize, "setPageWithoutSize")
        onPageSizeCalculated(pageSize)
    }

    fun setPage(page: Page, size: PointF) {
        if (!setPage(page)) {
            return
        }
        drawPage(size, "")
    }

    private fun drawPage(size: PointF, debugTag: String = "") {
        // Cancel pending render task
        cancelDrawEntireTask()
        isBlank = false

        if (page?.type == PageType.panorama) {
            // Panorama pages should start in some kind of zoomed state
            // which will give the view twice the width
            sourceScale = min(parentSize.x * 2f / size.x, parentSize.y / size.y)
        } else {
            // Calculate scaled size that fits within the screen limits
            // This is the size at minimum zoom
            sourceScale = min(parentSize.x / size.x, parentSize.y / size.y)
        }


        val newSize = Point((size.x * sourceScale).toInt(), (size.y * sourceScale).toInt())
        minZoomSize = newSize

        clearEntire()
        val entireBm = getEntireBitmap(newSize)

        // Render the page in the background
        this.drawEntire = object : CancellableAsyncTask<Void?, Void?>(
            getDrawPageTask(
                entireBm,
                newSize.x,
                newSize.y,
                0,
                0,
                newSize.x,
                newSize.y,
                "drawPage/$debugTag (${page?.pagePdf?.name})",
            )
        ) {
            override fun onPreExecute() {
                setBackgroundColor(BACKGROUND_COLOR)
                entire.apply {
                    setImageBitmap(null)
                    invalidate()
                }
                if (busyIndicator == null) {
                    busyIndicator = ProgressBar(context)
                        .apply {
                            isIndeterminate = true
                            visibility = INVISIBLE
                        }
                        .also {
                            addView(it)
                        }

                    _handler.postDelayed({
                        busyIndicator?.visibility = VISIBLE
                    }, PROGRESS_DIALOG_DELAY.toLong())
                }
            }

            override fun onPostExecute(result: Void?) {
                removeBusyIndicator()

                entire.apply {
                    setImageBitmap(entireBm)
                    invalidate()
                }
                setBackgroundColor(Color.TRANSPARENT)
            }
        }
            .apply {
                execute()
            }
        requestLayout()
    }

    private fun getEntireBitmap(size: Point): Bitmap {
        val currentBm = entireBm
        if (currentBm != null && currentBm.width == size.x && currentBm.height == size.y) {
            return currentBm
        } else {
            val newBm = Bitmap.createBitmap(size.x, size.y, Bitmap.Config.ARGB_8888)
            entireBm = newBm
            return newBm
        }
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val x = when (MeasureSpec.getMode(widthMeasureSpec)) {
            MeasureSpec.UNSPECIFIED -> minZoomSize.x
            else -> MeasureSpec.getSize(widthMeasureSpec)
        }
        val y = when (MeasureSpec.getMode(heightMeasureSpec)) {
            MeasureSpec.UNSPECIFIED -> minZoomSize.y
            else -> MeasureSpec.getSize(heightMeasureSpec)
        }
        setMeasuredDimension(x, y)

        busyIndicator?.apply {
            val limit = min(parentSize.x, parentSize.y) / 2
            measure(MeasureSpec.AT_MOST or limit, MeasureSpec.AT_MOST or limit)
        }
    }

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        val w = right - left
        val h = bottom - top

        entire.apply {
            if (width != w || height != h) {
                entireMat.setScale(w / minZoomSize.x.toFloat(), h / minZoomSize.y.toFloat())
                imageMatrix = entireMat
                invalidate()
            }
            layout(0, 0, w, h)
        }

        runIfNotNull(patchViewSize, patchArea) { patchViewSize, patchArea ->
            // If the patch view size does not match the current view size,
            // then the pdf was zoomed again before this layout run.
            // We will have to throw away the current patch and let the
            if (patchViewSize.x != w || patchViewSize.y != h) {
                clearPatch()
            } else {
                patch.layout(patchArea.left, patchArea.top, patchArea.right, patchArea.bottom)
            }
        }

        busyIndicator?.apply {
            val bw = measuredWidth
            val bh = measuredHeight
            layout((w - bw) / 2, (h - bh) / 2, (w + bw) / 2, (h + bh) / 2)
        }
    }


    fun updateHq(update: Boolean) {
        val viewArea = Rect(left, top, right, bottom)
        if (viewArea.width() == minZoomSize.x || viewArea.height() == minZoomSize.y) {
            // If the viewArea's size matches the unzoomed size, there is no need for an hq patch
            clearPatch()
        } else {
            val patchViewSize = Point(viewArea.width(), viewArea.height())
            val patchArea = Rect(0, 0, parentSize.x, parentSize.y)

            // Intersect and test that there is an intersection
            if (!patchArea.intersect(viewArea)) return

            // Offset patch area to be relative to the view top left
            patchArea.offset(-viewArea.left, -viewArea.top)
            val areaUnchanged = patchArea == this.patchArea && patchViewSize == this.patchViewSize

            // If being asked for the same area as last time and not because of an update then nothing to do
            if (areaUnchanged && !update) return
            val completeRedraw = !(areaUnchanged && update)

            // Stop the drawing of previous patch if still going
            cancelDrawPatchTask()

            // Create and add the image view if not already done
            val task: CancellableTaskDefinition<Void?, Void?> =
                if (completeRedraw) {
                    getDrawPageTask(
                        patchBm, patchViewSize.x, patchViewSize.y,
                        patchArea.left, patchArea.top,
                        patchArea.width(), patchArea.height(),
                        "updateHq($update) (${page?.pagePdf?.name})"
                    )
                } else {
                    getUpdatePageTask(
                        patchBm, patchViewSize.x, patchViewSize.y,
                        patchArea.left, patchArea.top,
                        patchArea.width(), patchArea.height(),
                        "updateHq($update) (${page?.pagePdf?.name})"
                    )
                }

            drawPatch = object : CancellableAsyncTask<Void?, Void?>(task) {
                override fun onPostExecute(result: Void?) {
                    this@PageView.patchViewSize = patchViewSize
                    this@PageView.patchArea = patchArea
                    patch.setImageBitmap(patchBm)
                    patch.invalidate()
                    //requestLayout();
                    // Calling requestLayout here doesn't lead to a later call to layout. No idea
                    // why, but apparently others have run into the problem.
                    patch.layout(patchArea.left, patchArea.top, patchArea.right, patchArea.bottom)
                }
            }.apply {
                execute()
            }
        }
    }

    fun removeHq() {
        // Stop the drawing of the patch if still going
        cancelDrawPatchTask()

        // And get rid of it
        clearPatch()
    }

    override fun isOpaque(): Boolean {
        return true
    }

    private fun getDrawPageTask(
        bm: Bitmap, sizeX: Int, sizeY: Int,
        patchX: Int, patchY: Int, patchWidth: Int, patchHeight: Int,
        debugTag: String
    ): CancellableTaskDefinition<Void?, Void?> {
        return object : MuPDFCancellableTaskDefinition<Void, Void>(debugTag) {
            override fun doInBackground(cookie: Cookie, vararg params: Void): Void? {
                // Workaround bug in Android Honeycomb 3.x, where the bitmap generation count
                // is not incremented when drawing.
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB && Build.VERSION.SDK_INT < Build.VERSION_CODES.ICE_CREAM_SANDWICH) {
                    bm.eraseColor(0)
                }

                muPDFCore?.drawPage(
                    bm,
                    0,
                    sizeX,
                    sizeY,
                    patchX,
                    patchY,
                    patchWidth,
                    patchHeight,
                    cookie
                )
                return null
            }
        }
    }

    private fun getUpdatePageTask(
        bm: Bitmap, sizeX: Int, sizeY: Int,
        patchX: Int, patchY: Int, patchWidth: Int, patchHeight: Int,
        debugTag: String
    ): CancellableTaskDefinition<Void?, Void?> {
        return object : MuPDFCancellableTaskDefinition<Void, Void>(debugTag) {
            override fun doInBackground(cookie: Cookie, vararg params: Void): Void? {
                // Workaround bug in Android Honeycomb 3.x, where the bitmap generation count
                // is not incremented when drawing.
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB && Build.VERSION.SDK_INT < Build.VERSION_CODES.ICE_CREAM_SANDWICH) {
                    bm.eraseColor(0)
                }
                muPDFCore?.updatePage(
                    bm,
                    0,
                    sizeX,
                    sizeY,
                    patchX,
                    patchY,
                    patchWidth,
                    patchHeight,
                    cookie
                )
                return null
            }
        }
    }

}