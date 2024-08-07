package de.taz.app.android.ui.pdfViewer.mupdf

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Point
import android.graphics.PointF
import android.util.SparseArray
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import de.taz.app.android.api.models.Page

class PageAdapter(
    private val mContext: Context,
    initialPages: List<Page>
) : BaseAdapter() {

    private var pages = initialPages

    private val pageSizesCache = SparseArray<PointF>()
    private var sharedHqBm: Bitmap? = null

    override fun getCount() = pages.size

    override fun getItem(position: Int): Page {
        return pages[position]
    }

    override fun getItemId(position: Int): Long {
        return 0
    }

    fun update(newPages: List<Page>): Array<Boolean> {
        require(pages.size == newPages.size) { "You can only update the existing Pages, not remove or add new ones. The new pages size must match the initial pages size." }

        val updated = Array(pages.size) { false }
        for (i in pages.indices) {
            val pageWasDownloaded = pages[i].dateDownload == null && newPages[i].dateDownload != null
            val checksumChanged = pages[i].pagePdf.sha256 != newPages[i].pagePdf.sha256
            updated[i] = pageWasDownloaded || checksumChanged
        }

        pages = newPages
        return updated
    }

    fun releaseBitmaps() {
        // release the shared bitmap and wait for the GC to recycle it
        sharedHqBm = null
    }

    fun refresh() {
        pageSizesCache.clear()
    }

    private fun createPageView(parent: ViewGroup): PageView {
        val cachedSharedHqBm = sharedHqBm
        val hqBitmap =
            if (cachedSharedHqBm == null || cachedSharedHqBm.width != parent.width || cachedSharedHqBm.height != parent.height) {
                Bitmap.createBitmap(parent.width, parent.height, Bitmap.Config.ARGB_8888).also {
                    sharedHqBm = it
                }
            } else {
                cachedSharedHqBm
            }

        return PageView(mContext, Point(parent.width, parent.height), hqBitmap)
    }

    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {

        val pageView: PageView = (convertView as? PageView) ?: createPageView(parent)

        val page = pages[position]
        val pageSize = pageSizesCache[position]

        if (page.dateDownload == null) {
            pageView.blank()
        } else if (pageSize != null) {
            // We already know the page size. Set it up immediately
            pageView.setPage(page, pageSize)
        } else {
            // Page size as yet unknown. Render the page and return its page size to be stored on this adapters cache.
            pageView.setPage(page) { pageSize ->
                pageSizesCache.put(position, pageSize)
            }
        }
        return pageView
    }
}