package de.taz.app.android.ui.pdfViewer

import android.os.Bundle
import android.util.TypedValue
import android.view.View
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
import de.taz.app.android.api.models.Image
import de.taz.app.android.base.NightModeActivity
import de.taz.app.android.monkey.*
import de.taz.app.android.persistence.repository.IssueKey
import de.taz.app.android.ui.main.MainActivity.Companion.KEY_ISSUE_KEY
import de.taz.app.android.util.Log
import kotlinx.android.synthetic.main.activity_pdf_pager.*
import kotlinx.coroutines.*
import kotlin.math.min

class PdfPagerActivity : NightModeActivity(R.layout.activity_pdf_pager) {

    private val log by Log
    private lateinit var viewPager2: ViewPager2
    private lateinit var issueKey: IssueKey
    private lateinit var pdfPagerViewModel: PdfPagerViewModel

    private var navButton: Image? = null
    private var navButtonAlpha = 255f
    lateinit var drawerLayout: DrawerLayout
    private lateinit var drawerAdapter: PdfDrawerRecyclerViewAdapter


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        issueKey = intent.getParcelableExtra(KEY_ISSUE_KEY)!!
        pdfPagerViewModel = getViewModel { PdfPagerViewModel(application, issueKey) }

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

        pdfPagerViewModel.pdfDataListModel.observe(this, {
            if (it.isNotEmpty()) {
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
            }
        })


        pdfPagerViewModel.userInputEnabled.observe(this, {
            viewPager2.isUserInputEnabled = it
        })

        pdfPagerViewModel.currentItem.observe(this, {
            viewPager2.currentItem = it
        })

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
        drawerAdapter = PdfDrawerRecyclerViewAdapter(items)
        navigation_recycler_view.adapter = drawerAdapter
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

    /**
     * A simple pager adapter that represents pdfFragment objects, in
     * sequence.
     */
    private inner class PdfPagerAdapter(fa: FragmentActivity) : FragmentStateAdapter(fa) {
        override fun createFragment(position: Int): Fragment {
            return PdfRenderFragment(position)
        }

        override fun getItemCount(): Int {
            return pdfPagerViewModel.getAmountOfPdfPages()
        }
    }
}