package de.taz.app.android.ui.webview

import android.graphics.Point
import android.os.Bundle
import android.util.TypedValue
import android.util.TypedValue.COMPLEX_UNIT_DIP
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import androidx.annotation.IdRes
import androidx.core.view.marginLeft
import androidx.core.view.marginRight
import androidx.core.widget.NestedScrollView
import androidx.core.widget.TextViewCompat
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.SavedStateViewModelFactory
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import com.google.android.material.appbar.AppBarLayout
import de.taz.app.android.R
import de.taz.app.android.WEEKEND_TYPEFACE_RESOURCE_FILE_NAME
import de.taz.app.android.api.models.SectionStub
import de.taz.app.android.persistence.repository.SectionRepository
import de.taz.app.android.singletons.DateHelper
import de.taz.app.android.singletons.FontHelper
import de.taz.app.android.ui.webview.pager.DisplayableScrollposition
import de.taz.app.android.ui.webview.pager.IssueContentViewModel
import de.taz.app.android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.ceil

class SectionWebViewViewModel(savedStateHandle: SavedStateHandle) :
    WebViewViewModel<SectionStub>(savedStateHandle)

const val PADDING_RIGHT_OF_LOGO = 20
const val BIG_HEADER_TEXT_SIZE = 30f

class SectionWebViewFragment :
    WebViewFragment<SectionStub, SectionWebViewViewModel>(R.layout.fragment_webview_section) {


    override val viewModel by lazy {
        ViewModelProvider(
            this, SavedStateViewModelFactory(
                this.requireActivity().application, this
            )
        ).get(SectionWebViewViewModel::class.java)
    }

    private val issueContentViewModel by lazy {
        ViewModelProvider(
            this.requireActivity(), SavedStateViewModelFactory(
                this.requireActivity().application, this.requireActivity()
            )
        ).get(IssueContentViewModel::class.java)
    }

    override val nestedScrollViewId: Int = R.id.web_view_wrapper

    private lateinit var sectionFileName: String

    companion object {
        private val log by Log
        private const val SECTION_FILE_NAME = "SECTION_FILE_NAME"
        fun createInstance(sectionFileName: String): SectionWebViewFragment {
            val args = Bundle()
            log.debug("SectionWebViewFragment.createInstance($sectionFileName)")
            args.putString(SECTION_FILE_NAME, sectionFileName)
            return SectionWebViewFragment().apply {
                arguments = args
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        sectionFileName = requireArguments().getString(SECTION_FILE_NAME)!!
        log.debug("Creating a SectionWebViewFragmen for $sectionFileName")
        lifecycleScope.launch(Dispatchers.IO) {
            // Because of lazy initialization the first call to viewModel needs to be on Main thread - TODO: Fix this
            withContext(Dispatchers.Main) { viewModel }
            viewModel.displayableLiveData.postValue(
                SectionRepository.getInstance().getStubOrThrow(sectionFileName)
            )
        }
    }

    override fun onPageRendered() {
        super.onPageRendered()
        val scrollView = view?.findViewById<NestedScrollView>(nestedScrollViewId)
        issueContentViewModel.lastScrollPositionOnDisplayable?.let {
            if (it.displayableKey == sectionFileName) {
                log.debug("The last scroll position was on this section, resetting to $it")
                scrollView?.scrollY = it.scrollPosition
            }
        }
        scrollView?.setOnScrollChangeListener { _: NestedScrollView?, _: Int, scrollY: Int, _: Int, _: Int ->
            issueContentViewModel.lastScrollPositionOnDisplayable =
                DisplayableScrollposition(sectionFileName, scrollY)
        }
    }

    override fun setHeader(displayable: SectionStub) {
        activity?.apply {

            lifecycleScope.launch(Dispatchers.IO) {
                val issueOperations = displayable.getIssueOperations(context?.applicationContext)
                issueOperations?.apply {
                    if (isWeekend) {
                        withContext(Dispatchers.Main) {
                            view?.findViewById<TextView>(R.id.section)?.typeface =
                                FontHelper.getInstance(context?.applicationContext)
                                    .getTypeFace(WEEKEND_TYPEFACE_RESOURCE_FILE_NAME)
                            //following two lines align header/dotted line in weekend issues
                            //with text in taz logo; TODO check whether we can get rid of them later
                            view?.findViewById<AppBarLayout>(R.id.app_bar_layout)?.translationY =
                                18f
                            view?.findViewById<AppWebView>(R.id.web_view)?.translationY = 18f
                        }
                    }
                }
            }

            runOnUiThread {
                view?.findViewById<TextView>(R.id.section)?.apply {
                    text = displayable.getHeaderTitle()
                }
                DateHelper.dateToLowerCaseString(displayable.issueDate)?.let {
                    view?.findViewById<TextView>(R.id.issue_date)?.apply {
                        text = it
                    }
                }

                // On first section "die tageszeitung" the header should be bigger:
                if (displayable.getHeaderTitle() == getString(R.string.fragment_default_header_title)) {
                    view?.findViewById<TextView>(R.id.section)?.apply {
                        setTextSize(COMPLEX_UNIT_DIP, BIG_HEADER_TEXT_SIZE)
                        TextViewCompat.setAutoSizeTextTypeUniformWithConfiguration(
                            this,
                            TextViewCompat.getAutoSizeMinTextSize(this),
                            (BIG_HEADER_TEXT_SIZE * resources.displayMetrics.density).toInt(),
                            ceil(0.1 * resources.displayMetrics.density).toInt(),
                            TypedValue.COMPLEX_UNIT_PX
                        )
                    }
                }

                activity?.findViewById<ImageView>(R.id.drawer_logo)?.let {
                    resizeHeaderSectionTitle(it.width)
                }
            }
        }
    }

    override fun onResume() {
        activity?.findViewById<ImageView>(R.id.drawer_logo)?.let {
            resizeHeaderSectionTitle(it.width)
            it.addOnLayoutChangeListener(resizeDrawerLogoListener)
        }
        super.onResume()
    }

    override fun onPause() {
        activity?.findViewById<ImageView>(R.id.drawer_logo)?.removeOnLayoutChangeListener(
            resizeDrawerLogoListener
        )
        super.onPause()
    }

    private val resizeDrawerLogoListener =
        View.OnLayoutChangeListener { v, _, _, _, _, _, _, _, _ ->
            resizeHeaderSectionTitle(v.width)
        }

    /**
     * ensure the text is not shown below the drawerLogo
     * @param drawerLogoWidth: Int - the width of the current logo shown in the drawer
     */
    private fun resizeHeaderSectionTitle(drawerLogoWidth: Int) {
        setMaxSizeDependingOnDrawerLogo(R.id.section, drawerLogoWidth)
        setMaxSizeDependingOnDrawerLogo(R.id.issue_date, drawerLogoWidth)
    }

    private fun setMaxSizeDependingOnDrawerLogo(@IdRes viewId: Int, drawerLogoWidth: Int) {
        val point = Point()
        activity?.windowManager?.defaultDisplay?.getSize(point)
        view?.findViewById<TextView>(viewId)?.apply {
            val parentView = (parent as View)
            val paddingInPixel = (PADDING_RIGHT_OF_LOGO / resources.displayMetrics.density).toInt()
            width =
                point.x - drawerLogoWidth - parentView.marginRight - marginLeft - marginRight - paddingInPixel
        }
    }
}