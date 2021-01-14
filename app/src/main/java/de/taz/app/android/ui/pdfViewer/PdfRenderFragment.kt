package de.taz.app.android.ui.pdfViewer

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.RelativeLayout
import com.artifex.mupdf.viewer.MuPDFCore
import com.artifex.mupdf.viewer.PageAdapter
import de.taz.app.android.R
import de.taz.app.android.api.models.Frame
import de.taz.app.android.base.BaseMainFragment
import de.taz.app.android.persistence.repository.IssueKey
import de.taz.app.android.ui.WelcomeActivity
import de.taz.app.android.ui.settings.SettingsActivity
import de.taz.app.android.util.Log
import java.io.File


class PdfRenderFragment : BaseMainFragment(R.layout.fragment_pdf_render) {

    val log by Log
    var pdfPage: File? = null
    var frameList: List<Frame> = emptyList()
    lateinit var issueKey: IssueKey

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
            val pdfReaderView = MuPDFReaderView(context, frameList, issueKey)
            pdfReaderView.adapter = PageAdapter(context, core)
            muPdfWrapper.addView(pdfReaderView)
        }
        return view
    }

    override fun onBottomNavigationItemClicked(menuItem: MenuItem) {
        when (menuItem.itemId) {
            R.id.bottom_navigation_action_home -> requireActivity().finish()
            R.id.bottom_navigation_action_settings -> {
                Intent(requireActivity(), SettingsActivity::class.java).apply {
                    startActivity(this)
                }
            }
            R.id.bottom_navigation_action_help -> {
                Intent(requireActivity(), WelcomeActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NO_ANIMATION
                    startActivity(this)
                }
            }
        }
    }
}
