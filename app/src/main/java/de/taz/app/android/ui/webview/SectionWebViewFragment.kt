package de.taz.app.android.ui.webview

import android.content.Context
import android.graphics.Point
import android.os.Bundle
import android.util.TypedValue
import android.util.TypedValue.COMPLEX_UNIT_SP
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import androidx.annotation.IdRes
import androidx.core.view.marginLeft
import androidx.core.view.marginRight
import androidx.core.widget.TextViewCompat
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.SavedStateViewModelFactory
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import com.google.android.material.appbar.AppBarLayout
import de.taz.app.android.R
import de.taz.app.android.WEEKEND_TYPEFACE_RESOURCE_FILE_NAME
import de.taz.app.android.api.models.Section
import de.taz.app.android.persistence.repository.FileEntryRepository
import de.taz.app.android.persistence.repository.SectionRepository
import de.taz.app.android.singletons.DateHelper
import de.taz.app.android.singletons.FontHelper
import de.taz.app.android.singletons.StorageService
import de.taz.app.android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.ceil

class SectionWebViewViewModel(savedStateHandle: SavedStateHandle) :
    WebViewViewModel<Section>(savedStateHandle)

const val PADDING_RIGHT_OF_LOGO = 20

class SectionWebViewFragment :
    WebViewFragment<Section, SectionWebViewViewModel>(R.layout.fragment_webview_section) {

    private lateinit var sectionRepository: SectionRepository
    private lateinit var fileEntryRepository: FileEntryRepository
    private lateinit var storageService: StorageService

    override val viewModel by lazy {
        ViewModelProvider(
            this, SavedStateViewModelFactory(
                this.requireActivity().application, this
            )
        ).get(SectionWebViewViewModel::class.java)
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
        log.debug("Creating a SectionWebViewFragment for $sectionFileName")
        lifecycleScope.launch(Dispatchers.IO) {
            // Because of lazy initialization the first call to viewModel needs to be on Main thread - TODO: Fix this
            withContext(Dispatchers.Main) { viewModel }
            viewModel.displayableLiveData.postValue(
                sectionRepository.get(sectionFileName)
            )
        }
    }

    override fun onAttach(context: Context) {
        super.onAttach(context)
        sectionRepository = SectionRepository.getInstance(requireContext().applicationContext)
        fileEntryRepository = FileEntryRepository.getInstance(requireContext().applicationContext)
        storageService = StorageService.getInstance(requireContext().applicationContext)
    }

    override fun setHeader(displayable: Section) {
        activity?.apply {

            lifecycleScope.launch(Dispatchers.IO) {
                val issueStub = displayable.getIssueStub()
                issueStub?.apply {
                    if (isWeekend) {
                        val weekendTypefaceFileEntry =
                            fileEntryRepository.get(WEEKEND_TYPEFACE_RESOURCE_FILE_NAME)
                        val weekendTypefaceFile = weekendTypefaceFileEntry?.let(storageService::getFile)
                        val weekendTypeface = weekendTypefaceFile?.let {
                            FontHelper.getInstance(context?.applicationContext)
                                .getTypeFace(it)
                        }

                        withContext(Dispatchers.Main) {
                            view?.findViewById<TextView>(R.id.section)?.typeface = weekendTypeface
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
                    val textPixelSize = resources.getDimensionPixelSize(R.dimen.fragment_header_title_section_text_size)
                    val textSpSize = resources.getDimension(R.dimen.fragment_header_title_section_text_size)
                    view?.findViewById<TextView>(R.id.section)?.apply {
                        setTextSize(COMPLEX_UNIT_SP, textSpSize)
                        TextViewCompat.setAutoSizeTextTypeUniformWithConfiguration(
                            this,
                            TextViewCompat.getAutoSizeMinTextSize(this),
                            textPixelSize,
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