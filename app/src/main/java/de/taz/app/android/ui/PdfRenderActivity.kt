package de.taz.app.android.ui

import android.content.Intent
import android.os.Bundle
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.viewpager2.adapter.FragmentStateAdapter
import androidx.viewpager2.widget.ViewPager2
import de.taz.app.android.R
import de.taz.app.android.api.models.Issue
import de.taz.app.android.api.models.IssueStatus
import de.taz.app.android.base.NightModeActivity
import de.taz.app.android.monkey.reduceDragSensitivity
import de.taz.app.android.persistence.repository.IssueRepository
import de.taz.app.android.singletons.FileHelper
import de.taz.app.android.util.Log
import io.sentry.core.Sentry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import java.io.File

class PdfRenderActivity : NightModeActivity(R.layout.activity_pdf_renderer) {

    val log by Log
    private lateinit var viewPager2: ViewPager2
    private lateinit var pagerAdapter: PdfRenderActivity.PdfPagerAdapter
    private var file: File? = null
    private var issue: Issue? = null
    private var pdfList: List<File> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val fileHelper = FileHelper.getInstance()
        try {

            // Instantiate a ViewPager
            viewPager2 = findViewById(R.id.activity_pdf_pager)

            // Instantiate pager adapter, which provides the pages to the view pager widget.
            pagerAdapter = PdfPagerAdapter(this@PdfRenderActivity)
            viewPager2.adapter = pagerAdapter

            viewPager2.apply {
                reduceDragSensitivity(6)
                offscreenPageLimit = 2
            }

            // TODO: get date and feed from intent eg = intent.extras?.getString(Intent.EXTRA_TEXT)
            runBlocking(Dispatchers.IO) {
                issue = IssueRepository.getInstance().getIssueByFeedAndDate(
                    "taz", "2020-11-24", IssueStatus.regular
                )
                pdfList = issue?.pageList?.map {
                    fileHelper.getFile(it.pagePdf)
                } ?: emptyList()
                log.debug("first of pdfList: ${pdfList.first().name} with length: ${pdfList.size}!!!")
            }

        } catch (e: NullPointerException) {
            val hint = "no FILENAME given as parameter, finishing PdfRendererActivity"
            log.error(hint)
            Sentry.captureMessage(hint)
            finish()
        }

    }

    /**
     * A simple pager adapter that represents pdfFragment objects, in
     * sequence.
     */
    private inner class PdfPagerAdapter(fa: FragmentActivity) : FragmentStateAdapter(fa) {
        override fun createFragment(position: Int): Fragment {
            return PdfFragment.createInstance(
                pdfList[position]
            )
        }

        override fun getItemCount(): Int {
            return pdfList.size
        }
    }
}