package de.taz.app.android.ui.pdfViewer

import android.os.Bundle
import android.util.TypedValue
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
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
import de.taz.app.android.data.DataService
import de.taz.app.android.monkey.*
import de.taz.app.android.persistence.repository.ImageRepository
import de.taz.app.android.persistence.repository.IssueKeyWithPages
import de.taz.app.android.singletons.DateHelper
import de.taz.app.android.ui.DRAWER_OVERLAP_OFFSET
import de.taz.app.android.util.Log
import kotlinx.android.synthetic.main.activity_pdf_drawer_layout.*
import kotlinx.android.synthetic.main.fragment_pdf_pager.*
import kotlinx.coroutines.*

const val LOGO_PEAK = 9

class PdfPagerActivity : NightModeActivity(R.layout.activity_pdf_drawer_layout) {

    companion object {
        const val KEY_ISSUE_KEY = "KEY_ISSUE_KEY"
    }

    private val log by Log

    private lateinit var issueKey: IssueKeyWithPages
    private lateinit var pdfPagerViewModel: PdfPagerViewModel
    private lateinit var imageRepository: ImageRepository
    private lateinit var dataService: DataService

    private var navButton: Image? = null
    private val navButtonAlpha = 255f
    lateinit var drawerLayout: DrawerLayout
    private lateinit var drawerAdapter: PdfDrawerRecyclerViewAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        dataService = DataService.getInstance(applicationContext)
        imageRepository = ImageRepository.getInstance(applicationContext)

        lifecycleScope.launch(Dispatchers.IO) {
            val baseUrl = dataService.getResourceInfo(retryOnFailure = true).resourceBaseUrl
            imageRepository.get(DEFAULT_NAV_DRAWER_FILE_NAME)?.let { image ->
                dataService.ensureDownloaded(FileEntry(image), baseUrl)
                showNavButton(
                    image
                )
            }
        }

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
                    log.debug("position clicked: $position. pdf")
                    if (position != pdfPagerViewModel.currentItem.value || position == 0) {
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

            override fun onDrawerSlide(drawerView: View, slideOffset: Float) {
                drawer_logo.animate().cancel()
                drawer_logo.translationX = 0f
                pdf_drawer_layout.updateDrawerLogoBoundingBox(
                    drawer_logo.width,
                    drawer_logo.height
                )
                (drawerView.parent as? View)?.let { parentView ->
                    val drawerWidth =
                        drawerView.width + (pdf_drawer_layout.drawerLogoBoundingBox?.width() ?: 0)
                    if (parentView.width < drawerWidth) {
                        drawer_logo.translationX = slideOffset * (parentView.width - drawerWidth)
                    } else {
                        drawer_logo.translationX = DRAWER_OVERLAP_OFFSET * resources.displayMetrics.density
                    }
                }
            }

            override fun onDrawerClosed(drawerView: View) {
                pdfPagerViewModel.hideDrawerLogo.postValue(true)
            }

            override fun onDrawerOpened(drawerView: View) = Unit
            override fun onDrawerStateChanged(newState: Int) = Unit
        })
        pdfPagerViewModel.pdfDataList.observe(this, {
            initDrawerAdapter(it)
        })
        pdfPagerViewModel.hideDrawerLogo.observe(this, { toHide ->
            val articlePagerFragment =
                supportFragmentManager.findFragmentByTag("IN_ARTICLE")
            if (toHide && articlePagerFragment == null) hideDrawerLogoWithDelay()
            else showDrawerLogo()
        })
    }

    private fun hideDrawerLogoWithDelay() {
        if (pdfPagerViewModel.hideDrawerLogo.value == true) {
            val transX = - drawer_logo.width.toFloat() + LOGO_PEAK * resources.displayMetrics.density
            drawer_logo.animate()
                .withEndAction{
                    pdf_drawer_layout.updateDrawerLogoBoundingBox(
                        (LOGO_PEAK * resources.displayMetrics.density).toInt(),
                        drawer_logo.height
                    )
                }
                .setDuration(1000L)
                .setStartDelay(2000L)
                .translationX(transX)
                .interpolator = AccelerateDecelerateInterpolator()
        }
    }

    private fun showDrawerLogo() {
        if (pdfPagerViewModel.hideDrawerLogo.value == false) {
            drawer_logo.animate()
                .withEndAction {
                    pdf_drawer_layout.updateDrawerLogoBoundingBox(
                        drawer_logo.width,
                        drawer_logo.height
                    )
                }
                .setDuration(1000L)
                .setStartDelay(0L)
                .translationX(0f)
                .interpolator = AccelerateDecelerateInterpolator()
        }
    }

    private fun initDrawerAdapter(items: List<PdfPageList>) {

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
            Glide
                .with(this)
                .load(items.first().pdfFile.absolutePath)
                .into(activity_pdf_drawer_front_page)

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