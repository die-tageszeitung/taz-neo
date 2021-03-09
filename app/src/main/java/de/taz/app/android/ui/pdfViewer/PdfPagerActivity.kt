package de.taz.app.android.ui.pdfViewer

import android.graphics.Bitmap
import android.os.Bundle
import android.util.TypedValue
import android.view.View
import androidx.core.content.ContextCompat
import androidx.drawerlayout.widget.DrawerLayout
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.GridLayoutManager.SpanSizeLookup
import com.bumptech.glide.Glide
import de.taz.app.android.DEFAULT_NAV_DRAWER_FILE_NAME
import de.taz.app.android.LOADING_SCREEN_FADE_OUT_TIME
import de.taz.app.android.R
import de.taz.app.android.api.models.FileEntry
import de.taz.app.android.api.models.Image
import de.taz.app.android.api.models.PageType
import de.taz.app.android.base.NightModeActivity
import de.taz.app.android.monkey.*
import de.taz.app.android.persistence.repository.IssueKey
import de.taz.app.android.singletons.DateHelper
import de.taz.app.android.ui.main.MainActivity.Companion.KEY_ISSUE_KEY
import de.taz.app.android.util.Log
import kotlinx.android.synthetic.main.activity_pdf_drawer_layout.*
import kotlinx.android.synthetic.main.fragment_pdf_pager.*
import kotlinx.coroutines.*
import java.io.File
import kotlin.math.min

class PdfPagerActivity : NightModeActivity(R.layout.activity_pdf_drawer_layout) {
    private val log by Log

    private lateinit var issueKey: IssueKey
    private lateinit var pdfPagerViewModel: PdfPagerViewModel

    private var navButton: Image? = null
    private var navButtonAlpha = 255f
    lateinit var drawerLayout: DrawerLayout
    private lateinit var drawerAdapter: PdfDrawerRecyclerViewAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        issueKey = try {
            intent.getParcelableExtra(KEY_ISSUE_KEY)!!
        } catch (e: NullPointerException) {
            throw IllegalStateException("PdfPagerActivity needs to be started with KEY_ISSUE_KEY in Intent extras of type IssueKey")
        }

        supportFragmentManager.beginTransaction().add(
            R.id.activity_pdf_fragment_placeholder,
            PdfPagerFragment()
        ).commit()

        pdfPagerViewModel = getViewModel { PdfPagerViewModel(application, issueKey) }

        drawerLayout = findViewById(R.id.pdf_drawer_layout)

        // Add Item Touch Listener
        navigation_recycler_view.addOnItemTouchListener(
            RecyclerTouchListener(
                this,
                fun(_: View, position: Int) {
                    if (position != pdfPagerViewModel.currentItem.value) {
                        val articlePagerFragment =
                            supportFragmentManager.findFragmentByTag("IN_ARTICLE")
                        if (articlePagerFragment != null && articlePagerFragment.isVisible) {
                            supportFragmentManager.popBackStack()
                        }
                        pdfPagerViewModel.currentItem.value = position
                        pdf_drawer_layout.closeDrawers()
                        drawerAdapter.activePosition = position
                    }
                }
            )
        )

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
        pdfPagerViewModel.pdfDataListModel.observe(this, {
            initDrawerAdapter(it)
        })
    }

    override fun onResume() {
        super.onResume()
        pdfPagerViewModel.setDefaultDrawerNavButton()
        pdfPagerViewModel.navButton.observeDistinct(this) {
            lifecycleScope.launch(Dispatchers.IO) {
                val baseUrl =
                    pdfPagerViewModel.dataService.getResourceInfo(retryOnFailure = true).resourceBaseUrl
                if (it != null) {
                    pdfPagerViewModel.dataService.ensureDownloaded(FileEntry(it), baseUrl)
                    showNavButton(it)
                } else {
                    pdfPagerViewModel.imageRepository.get(DEFAULT_NAV_DRAWER_FILE_NAME)
                        ?.let { image ->
                            pdfPagerViewModel.dataService.ensureDownloaded(
                                FileEntry(image),
                                baseUrl
                            )
                            showNavButton(
                                image
                            )
                        }
                }
            }
        }
    }

    private fun initDrawerAdapter(items: List<PdfPageListModel>) {

        if (items.isNotEmpty()) {
            // Setup a gridManager which takes 2 columns for panorama pages
            val gridLayoutManager = GridLayoutManager(this, 2)
            gridLayoutManager.spanSizeLookup = object : SpanSizeLookup() {
                override fun getSpanSize(position: Int): Int {
                    return if (items[position+1].pageType == PageType.panorama) {
                        2
                    } else {
                        1
                    }
                }
            }

            // Setup Recyclerview's Layout
            navigation_recycler_view.apply {
                layoutManager = gridLayoutManager
                setHasFixedSize(false)
            }

            // Setup drawer header (front page and date)
            activity_pdf_drawer_front_page.setImageBitmap(
                getPreviewImageFromPdfFile(items.first().pdfFile)
            )
            activity_pdf_drawer_front_page.setOnClickListener {
                pdf_viewpager.currentItem = 0
                activity_pdf_drawer_front_page_title.setTextColor(
                    ContextCompat.getColor(applicationContext, R.color.drawer_sections_item_highlighted)
                )
                drawerLayout.closeDrawers()
            }
            activity_pdf_drawer_front_page_title.apply {
                text = items.first().title
                setTextColor(
                    ContextCompat.getColor(applicationContext, R.color.drawer_sections_item_highlighted)
                )
            }
            activity_pdf_drawer_date.text = DateHelper.stringToLongLocalized2LineString(issueKey.date)

            drawerAdapter = PdfDrawerRecyclerViewAdapter(items.subList(1, items.size))
            pdfPagerViewModel.activePosition.observe(this, { position ->
                drawerAdapter.activePosition = position - 1
                if (position>0) {
                    activity_pdf_drawer_front_page_title?.setTextColor(
                        ContextCompat.getColor(applicationContext, R.color.drawer_sections_item)
                    )
                }
            })
            navigation_recycler_view.adapter = drawerAdapter
            hideLoadingScreen()
        }
    }

    private suspend fun showNavButton(navButton: Image) {
        if (this.navButton != navButton) {
            this.navButton = navButton
            val imageDrawable = withContext(Dispatchers.IO) {
                Glide
                    .with(this@PdfPagerActivity)
                    .load(pdfPagerViewModel.storageService.getAbsolutePath(navButton))
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

    private fun getPreviewImageFromPdfFile(file: File): Bitmap {
        val width = TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            DRAWER_PAGE_WIDTH,
            resources.displayMetrics
        ).toInt()
        return MuPDFThumbnail(file.path).thumbnail(width)
    }

    private fun hideLoadingScreen() {
        this.runOnUiThread {
            pdf_drawer_loading_screen?.animate()?.apply {
                alpha(0f)
                duration = LOADING_SCREEN_FADE_OUT_TIME
                withEndAction {
                    pdf_drawer_loading_screen?.visibility = View.GONE
                }
            }
        }
    }
}