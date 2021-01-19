package de.taz.app.android.ui.pdfViewer

import android.os.Bundle
import android.view.View
import androidx.drawerlayout.widget.DrawerLayout
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.viewpager2.adapter.FragmentStateAdapter
import androidx.viewpager2.widget.ViewPager2
import de.taz.app.android.R
import de.taz.app.android.WEBVIEW_DRAG_SENSITIVITY_FACTOR
import de.taz.app.android.api.models.Frame
import de.taz.app.android.api.models.Image
import de.taz.app.android.base.NightModeActivity
import de.taz.app.android.data.DataService
import de.taz.app.android.monkey.reduceDragSensitivity
import de.taz.app.android.persistence.repository.IssueKey
import de.taz.app.android.singletons.StorageService
import de.taz.app.android.ui.main.MainActivity.Companion.KEY_ISSUE_KEY
import de.taz.app.android.util.Log
import io.sentry.core.Sentry
import kotlinx.android.synthetic.main.activity_pdf_pager.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import java.io.File
import kotlin.math.min

class PdfPagerActivity: NightModeActivity(R.layout.activity_pdf_pager) {

    private val log by Log
    private lateinit var viewPager2: ViewPager2
    private lateinit var dataService: DataService
    private lateinit var storageService: StorageService
    private lateinit var issueKey: IssueKey
    private var listOfPdfWithFrameList: List<Pair<File, List<Frame>>> = emptyList()
    private var listOfPdfWithTitleAndPagina: List<Triple<File, String, String>> = emptyList()

    private var navButton: Image? = null
    private var navButtonAlpha = 255f
    lateinit var drawerLayout: DrawerLayout
    private lateinit var drawerAdapter: PdfDrawerRVAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        dataService = DataService.getInstance(applicationContext)
        storageService = StorageService.getInstance(applicationContext)

        drawerLayout = findViewById(R.id.pdf_drawer_layout)

        // Instantiate a ViewPager
        viewPager2 = findViewById(R.id.activity_pdf_pager)

        viewPager2.apply {
            adapter = PdfPagerAdapter(this@PdfPagerActivity)
            reduceDragSensitivity(WEBVIEW_DRAG_SENSITIVITY_FACTOR)
            offscreenPageLimit = 2
        }

        issueKey = intent.getParcelableExtra(KEY_ISSUE_KEY)!!

        runBlocking(Dispatchers.IO) {
            val issue = dataService.getIssue(issueKey)

            if (issue == null) {
                val hint = "Couldn't fetch issue $issueKey from dataService! Finishing this activity..."
                log.warn(hint)
                Sentry.captureMessage(hint)
                finish()
            } else {
                listOfPdfWithFrameList = issue.pageList.map {
                    storageService.getFile(it.pagePdf)!! to it.frameList!!
                }
                listOfPdfWithTitleAndPagina= issue.pageList.map {
                    Triple(storageService.getFile(it.pagePdf)!!, it.title!!, it.pagina!!)
                }
            }
        }

        pdf_drawer_logo.addOnLayoutChangeListener { v, _, _, _, _, _, _, _, _ ->
            log.debug("updateDrawerLogo. width: ${v.width} height: ${v.height}")
            pdf_drawer_layout.updateDrawerLogoBoundingBox(
                v.width,
                v.height
            )
        }

        pdf_drawer_layout.addDrawerListener(object : DrawerLayout.DrawerListener {
            var opened = false

            override fun onDrawerSlide(drawerView: View, slideOffset: Float) {
                (drawerView.parent as? View)?.let { parentView ->
                    val drawerWidth =
                        drawerView.width + (pdf_drawer_layout.drawerLogoBoundingBox?.width() ?: 0)
                    if (parentView.width < drawerWidth) {
                        pdf_drawer_logo.translationX = min(
                            slideOffset * (parentView.width - drawerWidth),
                            -5f * resources.displayMetrics.density
                        )
                    }
                }
            }

            override fun onDrawerOpened(drawerView: View) {
                log.debug("drawer opened!")
                opened = true
            }

            override fun onDrawerClosed(drawerView: View) {
                log.debug("drawer closed!")
                opened = false
            }

            override fun onDrawerStateChanged(newState: Int) {}
        })
    }

    /**
     * A simple pager adapter that represents pdfFragment objects, in
     * sequence.
     */
    private inner class PdfPagerAdapter(fa: FragmentActivity) : FragmentStateAdapter(fa) {
        override fun createFragment(position: Int): Fragment {
            return PdfRenderFragment.createInstance(
                listOfPdfWithFrameList[position],
                issueKey,
                viewPager2
            )
        }

        override fun getItemCount(): Int {
            return listOfPdfWithFrameList.size
        }

        fun getPositionOfPDf(pdfFileName: String): Int {
            return listOfPdfWithFrameList.indexOfFirst {
                it.first.name == pdfFileName
            }
        }
    }
}