package de.taz.app.android.ui

import android.graphics.Bitmap
import android.graphics.Point
import android.os.Bundle
import android.view.LayoutInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.RelativeLayout
import androidx.fragment.app.Fragment
import com.artifex.mupdf.viewer.MuPDFCore
import com.artifex.mupdf.viewer.PageAdapter
import de.taz.app.android.R
import de.taz.app.android.api.models.Frame
import de.taz.app.android.base.BaseMainFragment
import de.taz.app.android.persistence.repository.IssueKey
import de.taz.app.android.util.Log
import java.io.File


class PdfRenderFragment : BaseMainFragment(R.layout.fragment_pdf_render) {

    val log by Log
    var pdfPage: File? = null
    var frameList: List<Frame> = emptyList()
    var issueKey: IssueKey? = null

    companion object {
        fun createInstance(
            pdfPageWithFrameList: Pair<File, List<Frame>?>,
            issueKey: IssueKey
        ): PdfRenderFragment {
            val fragment = PdfRenderFragment()
            fragment.pdfPage = pdfPageWithFrameList.first
            fragment.frameList = pdfPageWithFrameList.second ?: emptyList()
            fragment.issueKey = issueKey
            return fragment
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {

        val view = inflater.inflate(R.layout.fragment_pdf_render, container, false)
        val muPdfWrapper = view.findViewById<RelativeLayout>(R.id.mu_pdf_wrapper)
        pdfPage?.let {
            val core = MuPDFCore(it.path)
            val pdfReaderView = MuPDFReaderView(context, frameList)
            pdfReaderView.adapter = PageAdapter(context, core)
            muPdfWrapper.addView(pdfReaderView)
        }
        return view
    }

    override fun onBottomNavigationItemClicked(menuItem: MenuItem) {
        log.debug("CLICKED ON BOTTOM NAVIGATION")
        if (menuItem.itemId == R.id.bottom_navigation_action_home) {
            requireActivity().finish()
        }
    }

}
