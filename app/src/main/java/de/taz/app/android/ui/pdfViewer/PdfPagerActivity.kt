package de.taz.app.android.ui.pdfViewer

import android.os.Bundle
import android.util.TypedValue
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import androidx.activity.viewModels
import androidx.core.content.ContextCompat
import androidx.core.view.GravityCompat
import androidx.drawerlayout.widget.DrawerLayout
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.GridLayoutManager.SpanSizeLookup
import com.bumptech.glide.Glide
import de.taz.app.android.ARTICLE_PAGER_FRAGMENT_FROM_PDF_MODE
import de.taz.app.android.LOADING_SCREEN_FADE_OUT_TIME
import de.taz.app.android.R
import de.taz.app.android.api.models.Image
import de.taz.app.android.api.models.PageType
import de.taz.app.android.base.NightModeActivity
import de.taz.app.android.monkey.*
import de.taz.app.android.persistence.repository.IssueKeyWithPages
import de.taz.app.android.singletons.DateHelper
import de.taz.app.android.singletons.StorageService
import de.taz.app.android.ui.DRAWER_OVERLAP_OFFSET
import de.taz.app.android.util.Log
import kotlinx.android.synthetic.main.activity_pdf_drawer_layout.*
import kotlinx.android.synthetic.main.activity_pdf_drawer_layout.drawer_logo
import kotlinx.android.synthetic.main.fragment_pdf_pager.*
import kotlinx.coroutines.*

const val LOGO_PEAK = 8
const val HIDE_LOGO_DELAY_MS = 1000L
const val LOGO_ANIMATION_DURATION_MS = 500L

class PdfPagerActivity : NightModeActivity(R.layout.activity_pdf_drawer_layout) {

    companion object {
        const val KEY_ISSUE_KEY = "KEY_ISSUE_KEY"
    }

    private val log by Log

    private var navButton: Image? = null
    private lateinit var issueKey: IssueKeyWithPages
    private val pdfPagerViewModel by viewModels<PdfPagerViewModel>()
    private lateinit var storageService: StorageService

    private val navButtonAlpha = 255f
    lateinit var drawerLayout: DrawerLayout
    private lateinit var drawerAdapter: PdfDrawerRecyclerViewAdapter
    private var drawerLogoWidth = 0f

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        issueKey = try {
            intent.getParcelableExtra(KEY_ISSUE_KEY)!!
        } catch (e: NullPointerException) {
            throw IllegalStateException("PdfPagerActivity needs to be started with KEY_ISSUE_KEY in Intent extras of type IssueKey")
        }
        if (savedInstanceState == null) {
            pdfPagerViewModel.issueKey.postValue(issueKey)
        }

        storageService = StorageService.getInstance(applicationContext)

        pdfPagerViewModel.navButton.observe(this) {
            if (it != null) {
                lifecycleScope.launch { showNavButton(it) }
            }
        }

        if (supportFragmentManager.findFragmentByTag(ARTICLE_PAGER_FRAGMENT_FROM_PDF_MODE) == null) {
            supportFragmentManager.beginTransaction().add(
                R.id.activity_pdf_fragment_placeholder,
                PdfPagerFragment()
            ).commit()
        }

        drawerLayout = findViewById(R.id.pdf_drawer_layout)

        // Add Item Touch Listener
        navigation_recycler_view.addOnItemTouchListener(
            RecyclerTouchListener(
                this,
                fun(_: View, drawerPosition: Int) {
                    log.debug("position clicked: $drawerPosition. pdf")
                    // currentItem.value begins from 0 to n-1th pdf page
                    // but in the drawer the front page is not part of the drawer list, that's why
                    // it needs to be incremented by 1:
                    val realPosition = drawerPosition + 1
                    val isFrontPage = drawerPosition == 0
                    if (realPosition != pdfPagerViewModel.currentItem.value || isFrontPage) {
                        pdfPagerViewModel.currentItem.value = realPosition
                        drawerAdapter.activePosition = drawerPosition
                    }
                    popArticlePagerFragmentIfOpen()
                    pdf_drawer_layout.closeDrawers()
                }
            )
        )

        pdf_drawer_layout.addDrawerListener(object : DrawerLayout.DrawerListener {
            override fun onDrawerSlide(drawerView: View, slideOffset: Float) {
                drawer_logo_wrapper.animate().cancel()
                drawer_logo_wrapper.translationX = resources.getDimension(R.dimen.drawer_logo_translation_x)
                pdf_drawer_layout.updateDrawerLogoBoundingBox(
                    drawer_logo_wrapper.width,
                    drawer_logo_wrapper.height
                )
                (drawerView.parent as? View)?.let { parentView ->
                    val drawerWidth =
                        drawerView.width + (pdf_drawer_layout.drawerLogoBoundingBox?.width() ?: 0)
                    if (parentView.width < drawerWidth) {
                        // translation needed for logo to be shown when drawer is too wide:
                        val offsetOnOpenDrawer =
                            slideOffset * (parentView.width - drawerWidth)
                        // translation needed when drawer is closed then:
                        val offsetOnClosedDrawer =
                            (1 - slideOffset) * DRAWER_OVERLAP_OFFSET * resources.displayMetrics.density
                        drawer_logo_wrapper.translationX = offsetOnOpenDrawer + offsetOnClosedDrawer
                    }
                }
            }

            override fun onDrawerClosed(drawerView: View) {
                pdfPagerViewModel.hideDrawerLogo.postValue(true)
            }

            override fun onDrawerOpened(drawerView: View) = Unit
            override fun onDrawerStateChanged(newState: Int) = Unit
        })

        pdfPagerViewModel.pdfPageList.observe(this, {
            initDrawerAdapter(it)
        })

        pdfPagerViewModel.hideDrawerLogo.observe(this, { toHide ->
            val articlePagerFragment =
                supportFragmentManager.findFragmentByTag(ARTICLE_PAGER_FRAGMENT_FROM_PDF_MODE)
            if (toHide && articlePagerFragment == null && !drawerLayout.isDrawerOpen(GravityCompat.START)) {
                hideDrawerLogoWithDelay()
            }
            else {
                showDrawerLogo()
            }
        })

        drawer_logo_wrapper.addOnLayoutChangeListener { v, _, _, _, _, _, _, _, _ ->
            pdf_drawer_layout.updateDrawerLogoBoundingBox(
                v.width,
                v.height
            )
        }
    }

    private fun hideDrawerLogoWithDelay() {
        if (pdfPagerViewModel.hideDrawerLogo.value == true) {
            val transX = - drawerLogoWidth + LOGO_PEAK * resources.displayMetrics.density
            drawer_logo_wrapper.animate()
                .withEndAction{
                    pdf_drawer_layout.updateDrawerLogoBoundingBox(
                        (LOGO_PEAK * resources.displayMetrics.density).toInt(),
                        drawer_logo_wrapper.height
                    )
                }
                .setDuration(LOGO_ANIMATION_DURATION_MS)
                .setStartDelay(HIDE_LOGO_DELAY_MS)
                .translationX(transX)
                .interpolator = AccelerateDecelerateInterpolator()
        }
    }

    private fun showDrawerLogo(hideAgainFlag: Boolean = true) {
        if (pdfPagerViewModel.hideDrawerLogo.value == false) {
            drawer_logo_wrapper.animate()
                .withEndAction {
                    pdf_drawer_layout.updateDrawerLogoBoundingBox(
                        drawerLogoWidth.toInt(),
                        drawer_logo_wrapper.height
                    )
                    if (hideAgainFlag) {
                        pdfPagerViewModel.hideDrawerLogo.postValue(true)
                    }
                }
                .setDuration(LOGO_ANIMATION_DURATION_MS)
                .setStartDelay(0L)
                .translationX(resources.getDimension(R.dimen.drawer_logo_translation_x))
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
                popArticlePagerFragmentIfOpen()
                activity_pdf_drawer_front_page_title.setTextColor(
                    ContextCompat.getColor(this, R.color.drawer_sections_item_highlighted)
                )
                drawerLayout.closeDrawers()
            }
            activity_pdf_drawer_front_page_title.apply {
                text = items.first().title
                setTextColor(
                    ContextCompat.getColor(this@PdfPagerActivity, R.color.drawer_sections_item_highlighted)
                )
            }
            activity_pdf_drawer_date.text = DateHelper.stringToLongLocalized2LineString(issueKey.date)

            drawerAdapter = PdfDrawerRecyclerViewAdapter(items.subList(1, items.size), Glide.with(this))
            pdfPagerViewModel.currentItem.observe(this, { position ->
                drawerAdapter.activePosition = position - 1
                if (position > 0) {
                    log.debug("set front page title color to: ${R.color.drawer_sections_item}")
                    activity_pdf_drawer_front_page_title?.setTextColor(
                        ContextCompat.getColor(this, R.color.drawer_sections_item)
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
                    .load(storageService.getAbsolutePath(navButton))
                    .submit()
                    .get()
            }

            // scale factor determined in resources
            val scaleFactor = resources.getFraction(
                R.fraction.nav_button_scale_factor,
                1,
                33
            )
            val logicalWidth = TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP,
                imageDrawable.intrinsicWidth.toFloat(),
                resources.displayMetrics
            ) * scaleFactor

            drawerLogoWidth = logicalWidth
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
                drawer_logo_wrapper.layoutParams.width = logicalWidth.toInt()
                drawer_logo_wrapper.layoutParams.height = logicalHeight.toInt()
                drawer_logo_wrapper.translationX = resources.getDimension(R.dimen.drawer_logo_translation_x)
                pdf_drawer_layout.requestLayout()
                drawer_logo_wrapper.requestLayout()
            }
            // Update the clickable bounding box:
            pdf_drawer_layout.updateDrawerLogoBoundingBox(
                drawerLogoWidth.toInt(),
                drawer_logo_wrapper.height
            )
        }
        if (!drawerLayout.isDrawerOpen(GravityCompat.START)) {
            pdfPagerViewModel.hideDrawerLogo.postValue(true)
        } else
            pdfPagerViewModel.hideDrawerLogo.postValue(false)
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

    private fun popArticlePagerFragmentIfOpen() {
        val articlePagerFragment =
            supportFragmentManager.findFragmentByTag(ARTICLE_PAGER_FRAGMENT_FROM_PDF_MODE)
        if (articlePagerFragment != null && articlePagerFragment.isVisible) {
            supportFragmentManager.popBackStack()
        }
    }
}