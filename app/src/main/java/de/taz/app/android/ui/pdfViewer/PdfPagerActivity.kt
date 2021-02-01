package de.taz.app.android.ui.pdfViewer

import android.os.Bundle
import android.util.TypedValue
import android.view.View
import androidx.activity.viewModels
import androidx.drawerlayout.widget.DrawerLayout
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import androidx.viewpager2.adapter.FragmentStateAdapter
import androidx.viewpager2.widget.ViewPager2
import com.bumptech.glide.Glide
import de.taz.app.android.DEFAULT_NAV_DRAWER_FILE_NAME
import de.taz.app.android.R
import de.taz.app.android.WEBVIEW_DRAG_SENSITIVITY_FACTOR
import de.taz.app.android.api.models.FileEntry
import de.taz.app.android.api.models.Frame
import de.taz.app.android.api.models.Image
import de.taz.app.android.base.NightModeActivity
import de.taz.app.android.data.DataService
import de.taz.app.android.monkey.observeDistinct
import de.taz.app.android.monkey.reduceDragSensitivity
import de.taz.app.android.persistence.repository.ImageRepository
import de.taz.app.android.persistence.repository.IssueKey
import de.taz.app.android.singletons.StorageService
import de.taz.app.android.ui.main.MainActivity.Companion.KEY_ISSUE_KEY
import de.taz.app.android.util.Log
import io.sentry.core.Sentry
import kotlinx.android.synthetic.main.activity_pdf_pager.*
import kotlinx.coroutines.*
import java.io.File
import kotlin.math.min

class PdfPagerActivity: NightModeActivity(R.layout.activity_pdf_pager) {

    private val log by Log
    private lateinit var viewPager2: ViewPager2
    private lateinit var dataService: DataService
    private lateinit var storageService: StorageService
    private lateinit var issueKey: IssueKey
    private var listOfPdfWithFrameList: List<Pair<File, List<Frame>>> = emptyList()
    private var listOfPdfWithTitleAndPagina: List<PdfDrawerItemModel> = emptyList()

    private var navButton: Image? = null
    private var navButtonAlpha = 255f
    lateinit var drawerLayout: DrawerLayout
    private lateinit var drawerAdapter: PdfDrawerRecyclerViewAdapter
    private lateinit var imageRepository: ImageRepository
    protected val pdfPagerViewModel: PdfPagerViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        dataService = DataService.getInstance(applicationContext)
        storageService = StorageService.getInstance(applicationContext)
        imageRepository = ImageRepository.getInstance(applicationContext)

        drawerLayout = findViewById(R.id.pdf_drawer_layout)

        // Setup Recyclerview's Layout
        navigation_recycler_view.layoutManager = GridLayoutManager(this, 2)
        navigation_recycler_view.setHasFixedSize(true)

        // Add Item Touch Listener
        navigation_recycler_view.addOnItemTouchListener(
            RecyclerTouchListener(
                this,
                fun(_: View, position: Int) {
                    if (position != viewPager2.currentItem) {
                        viewPager2.currentItem = position
                        pdf_drawer_layout.closeDrawers()
                        drawerAdapter.activePosition = position
                    }
                }
            )
        )

        // Instantiate a ViewPager
        viewPager2 = findViewById(R.id.activity_pdf_pager)

        viewPager2.apply {
            adapter = PdfPagerAdapter(this@PdfPagerActivity)
            reduceDragSensitivity(WEBVIEW_DRAG_SENSITIVITY_FACTOR)
            offscreenPageLimit = 2

            registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
                override fun onPageSelected(position: Int) {
                    log.debug("page selected: $position")
                    drawerAdapter.activePosition = position
                    super.onPageSelected(position)
                }
            })
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
                    PdfDrawerItemModel(storageService.getFile(it.pagePdf)!!, it.title!!, it.pagina!!)
                }
                initDrawerAdapter(listOfPdfWithTitleAndPagina)
            }
        }
        drawer_logo.addOnLayoutChangeListener { v, _, _, _, _, _, _, _, _ ->
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
                        drawer_logo.translationX = min(
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

            override fun onDrawerStateChanged(newState: Int) = Unit
        })
    }

    override fun onResume() {
        super.onResume()
        pdfPagerViewModel.setDefaultDrawerNavButton()
        pdfPagerViewModel.navButton.observeDistinct(this) {
            lifecycleScope.launch(Dispatchers.IO) {
                val baseUrl = dataService.getResourceInfo(retryOnFailure = true).resourceBaseUrl
                if (it != null) {
                    dataService.ensureDownloaded(FileEntry(it), baseUrl)
                    showNavButton(it)
                } else {
                    imageRepository.get(DEFAULT_NAV_DRAWER_FILE_NAME)?.let { image ->
                        dataService.ensureDownloaded(FileEntry(image), baseUrl)
                        showNavButton(
                            image
                        )
                    }
                }
            }
        }
    }

    private fun initDrawerAdapter(items: List<PdfDrawerItemModel>) {
        drawerAdapter = PdfDrawerRecyclerViewAdapter(items)
        navigation_recycler_view.adapter = drawerAdapter
    }

    private suspend fun showNavButton(navButton: Image) {
        if (this.navButton != navButton) {
            this.navButton = navButton
            val imageDrawable = withContext(Dispatchers.IO) {
                Glide
                    .with(this@PdfPagerActivity)
                    .load(storageService.getAbsolutePath(navButton))
                    .submit()
                    .get()
            }

            val scaleFactor = 1f / 3f
            val logicalWidth = TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP,
                imageDrawable.intrinsicWidth.toFloat(),
                resources.displayMetrics
            ) * scaleFactor

            val logicalHeight = TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP,
                imageDrawable.intrinsicHeight.toFloat(),
                resources.displayMetrics
            ) * scaleFactor

            withContext(Dispatchers.Main) {
                drawer_logo.setImageDrawable(imageDrawable)
                drawer_logo.alpha = navButtonAlpha
                drawer_logo.imageAlpha = (navButton.alpha * 255).toInt()
                drawer_logo.layoutParams.width = logicalWidth.toInt()
                drawer_logo.layoutParams.height = logicalHeight.toInt()
                pdf_drawer_layout.requestLayout()
            }
        }
    }

    fun getPositionOfPdf(fileName: String): Int {
        return listOfPdfWithFrameList.indexOfFirst {  it.first.name == fileName }
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
    }
}