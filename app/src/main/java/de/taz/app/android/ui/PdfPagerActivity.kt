package de.taz.app.android.ui

import android.os.Bundle
import android.view.View
import androidx.drawerlayout.widget.DrawerLayout
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.viewpager2.adapter.FragmentStateAdapter
import androidx.viewpager2.widget.ViewPager2
import de.taz.app.android.ISSUE_KEY
import de.taz.app.android.R
import de.taz.app.android.api.models.Frame
import de.taz.app.android.base.NightModeActivity
import de.taz.app.android.data.DataService
import de.taz.app.android.monkey.reduceDragSensitivity
import de.taz.app.android.persistence.repository.IssueKey
import de.taz.app.android.singletons.StorageService
import de.taz.app.android.util.Log
import kotlinx.android.synthetic.main.activity_taz_viewer.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import java.io.File
import kotlin.math.min

class PdfPagerActivity: NightModeActivity(R.layout.activity_pdf_pager) {

    val log by Log
    private lateinit var viewPager2: ViewPager2
    private lateinit var pagerAdapter: PdfPagerActivity.PdfPagerAdapter
    private lateinit var dataService: DataService
    private lateinit var storageService: StorageService
    private var issueKey: IssueKey? = null
    private var pdfList: List<File> = emptyList()
    private var listOfPdfWithFrameList : List<Pair<File, List<Frame>?>> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        dataService = DataService.getInstance(applicationContext)
        storageService = StorageService.getInstance(applicationContext)

        // Instantiate a ViewPager
        viewPager2 = findViewById(R.id.activity_pdf_pager)

        // Instantiate pager adapter, which provides the pages to the view pager widget
        pagerAdapter = PdfPagerAdapter(this)
        viewPager2.adapter = pagerAdapter

        viewPager2.apply {
            reduceDragSensitivity(7)
            offscreenPageLimit = 2
        }

        issueKey = intent.getParcelableExtra(ISSUE_KEY)
        issueKey?.let { iK ->
            runBlocking(Dispatchers.IO) {
                val issue = dataService.getIssue(iK)
                pdfList = issue?.pageList
                    ?.map { storageService.getFile(it.pagePdf)!! }
                    ?: emptyList()
                listOfPdfWithFrameList = issue?.pageList
                    ?.map { storageService.getFile(it.pagePdf)!! to it.frameList }
                    ?: emptyList()
            }
        } ?: run {
            log.warn("Could not fetch issue. IssueKey passed to activity?")
            finish()
        }


        drawer_logo.addOnLayoutChangeListener { v, _, _, _, _, _, _, _, _ ->
            drawer_layout.updateDrawerLogoBoundingBox(
                v.width,
                v.height
            )
        }

        drawer_layout.addDrawerListener(object : DrawerLayout.DrawerListener {
            var opened = false

            override fun onDrawerSlide(drawerView: View, slideOffset: Float) {
                (drawerView.parent as? View)?.let { parentView ->
                    val drawerWidth =
                        drawerView.width + (drawer_layout.drawerLogoBoundingBox?.width() ?: 0)
                    if (parentView.width < drawerWidth) {
                        drawer_logo.translationX = min(
                            slideOffset * (parentView.width - drawerWidth),
                            -5f * resources.displayMetrics.density
                        )
                    }
                }
            }

            override fun onDrawerOpened(drawerView: View) {
                opened = true
            }

            override fun onDrawerClosed(drawerView: View) {
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
                issueKey!!
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