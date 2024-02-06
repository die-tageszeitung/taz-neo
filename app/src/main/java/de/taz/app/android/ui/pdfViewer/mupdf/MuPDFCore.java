package de.taz.app.android.ui.pdfViewer.mupdf;

import android.graphics.Bitmap;
import android.graphics.PointF;

import com.artifex.mupdf.fitz.Cookie;
import com.artifex.mupdf.fitz.DisplayList;
import com.artifex.mupdf.fitz.Document;
import com.artifex.mupdf.fitz.Matrix;
import com.artifex.mupdf.fitz.Outline;
import com.artifex.mupdf.fitz.Page;
import com.artifex.mupdf.fitz.Rect;
import com.artifex.mupdf.fitz.RectI;
import com.artifex.mupdf.fitz.SeekableInputStream;
import com.artifex.mupdf.fitz.android.AndroidDrawDevice;

import de.taz.app.android.util.Log;
import io.sentry.Sentry;

public class MuPDFCore {
    private int resolution;
    private Document doc;
    private Outline[] outline;
    private int pageCount = -1;
    private boolean reflowable = false;
    private int currentPage;
    private Page page;
    private float pageWidth;
    private float pageHeight;
    private DisplayList displayList;

    /* Default to "A Format" pocket book size. */
    private int layoutW = 312;
    private int layoutH = 504;
    private int layoutEM = 10;

    private MuPDFCore(Document doc) {
        this.doc = doc;
        doc.layout(layoutW, layoutH, layoutEM);
        pageCount = doc.countPages();
        reflowable = doc.isReflowable();
        resolution = 160;
        currentPage = -1;
    }

    public MuPDFCore(byte buffer[], String magic) {
        this(Document.openDocument(buffer, magic));
    }

    public MuPDFCore(SeekableInputStream stm, String magic) {
        this(Document.openDocument(stm, magic));
    }

    public String getTitle() {
        return doc.getMetaData(Document.META_INFO_TITLE);
    }

    public int countPages() {
        return pageCount;
    }

    public boolean isReflowable() {
        return reflowable;
    }

    public synchronized int layout(int oldPage, int w, int h, int em) {
        if (w != layoutW || h != layoutH || em != layoutEM) {
            System.out.println("LAYOUT: " + w + "," + h);
            layoutW = w;
            layoutH = h;
            layoutEM = em;
            long mark = doc.makeBookmark(doc.locationFromPageNumber(oldPage));
            doc.layout(layoutW, layoutH, layoutEM);
            currentPage = -1;
            pageCount = doc.countPages();
            outline = null;
            try {
                outline = doc.loadOutline();
            } catch (Exception ex) {
                /* ignore error */
            }
            return doc.pageNumberFromLocation(doc.findBookmark(mark));
        }
        return oldPage;
    }

    private synchronized void gotoPage(int pageNum) {
        /* TODO: page cache */
        if (pageNum > pageCount - 1)
            pageNum = pageCount - 1;
        else if (pageNum < 0)
            pageNum = 0;
        if (pageNum != currentPage) {
            if (page != null)
                page.destroy();
            page = null;
            if (displayList != null)
                displayList.destroy();
            displayList = null;
            page = null;
            pageWidth = 0;
            pageHeight = 0;
            currentPage = -1;

            if (doc != null) {
                page = doc.loadPage(pageNum);
                Rect b = page.getBounds();
                pageWidth = b.x1 - b.x0;
                pageHeight = b.y1 - b.y0;
            } else {
                page = null;
                pageWidth = 0;
                pageHeight = 0;
            }

            currentPage = pageNum;
        }
    }

    public synchronized PointF getPageSize(int pageNum) {
        gotoPage(pageNum);
        return new PointF(pageWidth, pageHeight);
    }

    public synchronized void onDestroy() {
        if (displayList != null)
            displayList.destroy();
        displayList = null;
        if (page != null)
            page.destroy();
        page = null;
        if (doc != null)
            doc.destroy();
        doc = null;
    }

    public synchronized void drawPage(Bitmap bm, int pageNum,
                                      int pageW, int pageH,
                                      int patchX, int patchY,
                                      int patchW, int patchH,
                                      Cookie cookie) {
        gotoPage(pageNum);

        if (displayList == null && page != null)
            try {
                displayList = page.toDisplayList();
            } catch (Exception ex) {
                displayList = null;
            }

        if (displayList == null || page == null)
            return;

        float zoom = resolution / 72;
        Matrix ctm = new Matrix(zoom, zoom);
        RectI bbox = new RectI(page.getBounds().transform(ctm));
        float xscale = (float) pageW / (float) (bbox.x1 - bbox.x0);
        float yscale = (float) pageH / (float) (bbox.y1 - bbox.y0);
        ctm.scale(xscale, yscale);

        AndroidDrawDevice dev = new AndroidDrawDevice(bm, patchX, patchY);
        try {
            // Closing the device often throws a RuntimeException with the error:
            // "items left on stack in draw device: X"
            // https://sentry.taz.de/organizations/sentry/issues/4895
            // In this case we still want to try to destroy the device to free its memory,
            // and we don't want the app to crash.
            displayList.run(dev, ctm, cookie);
            dev.close();
        } catch (RuntimeException e) {
            String message = e.getMessage();
            if (message != null && message.startsWith("items left on stack in draw device:")) {
                Log log = new Log(MuPDFCore.class.getName());
                log.error("MuPDFCore crashed on close()", e);
                Sentry.captureException(e);
            } else {
                throw e;
            }
        } finally {
            dev.destroy();
        }
    }

    public synchronized void updatePage(Bitmap bm, int pageNum,
                                        int pageW, int pageH,
                                        int patchX, int patchY,
                                        int patchW, int patchH,
                                        Cookie cookie) {
        drawPage(bm, pageNum, pageW, pageH, patchX, patchY, patchW, patchH, cookie);
    }
}
