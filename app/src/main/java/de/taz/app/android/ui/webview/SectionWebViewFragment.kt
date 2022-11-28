package de.taz.app.android.ui.webview

import android.app.Application
import android.content.Context
import android.graphics.Point
import android.os.Bundle
import android.util.TypedValue
import android.util.TypedValue.COMPLEX_UNIT_SP
import android.view.LayoutInflater
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import androidx.annotation.IdRes
import androidx.core.view.marginLeft
import androidx.core.view.marginRight
import androidx.core.widget.TextViewCompat
import androidx.fragment.app.viewModels
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.lifecycleScope
import com.google.android.material.appbar.CollapsingToolbarLayout
import de.taz.app.android.R
import de.taz.app.android.WEEKEND_TYPEFACE_BOLD_RESOURCE_FILE_NAME
import de.taz.app.android.api.models.Section
import de.taz.app.android.databinding.FragmentWebviewSectionBinding
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

class SectionWebViewViewModel(application: Application, savedStateHandle: SavedStateHandle) :
    WebViewViewModel<Section>(application, savedStateHandle)

const val PADDING_RIGHT_OF_LOGO = 20

class SectionWebViewFragment : WebViewFragment<
        Section,
        SectionWebViewViewModel,
        FragmentWebviewSectionBinding
>() {

    private lateinit var sectionRepository: SectionRepository
    private lateinit var fileEntryRepository: FileEntryRepository
    private lateinit var storageService: StorageService

    override val viewModel by viewModels<SectionWebViewViewModel>()

    override val nestedScrollViewId: Int = R.id.web_view_wrapper

    private lateinit var sectionFileName: String

    companion object {
        private val log by Log
        private const val SECTION_FILE_NAME = "SECTION_FILE_NAME"
        fun newInstance(sectionFileName: String): SectionWebViewFragment {
            val args = Bundle()
            log.debug("SectionWebViewFragment.newInstance($sectionFileName)")
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

        lifecycleScope.launch {
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

            lifecycleScope.launch(Dispatchers.Main) {
                val issueStub = displayable.getIssueStub(requireContext().applicationContext)

                val toolbar =
                    view?.findViewById<CollapsingToolbarLayout>(R.id.collapsing_toolbar_layout)
                toolbar?.removeAllViews()

                // The first page of the weekend taz should not display the title but the date instead
                val layout =
                    if (issueStub?.isWeekend == true && displayable.getHeaderTitle() == getString(R.string.fragment_default_header_title)) {
                        R.layout.fragment_webview_header_title_weekend_section
                    } else {
                        R.layout.fragment_webview_header_section
                    }

                val headerView =
                    LayoutInflater.from(requireContext()).inflate(layout, toolbar, true)
                val sectionTextView = headerView.findViewById<TextView>(R.id.section)

                // Change typeface (to Knile) if it is weekend issue but not on title section:
                if (issueStub?.isWeekend == true && displayable.getHeaderTitle() != getString(R.string.fragment_default_week_header_title)) {
                    val weekendTypeface = withContext(Dispatchers.IO) {
                        val weekendTypefaceFileEntry =
                            fileEntryRepository.get(WEEKEND_TYPEFACE_BOLD_RESOURCE_FILE_NAME)
                        val weekendTypefaceFile =
                            weekendTypefaceFileEntry?.let(storageService::getFile)
                        weekendTypefaceFile?.let {
                            FontHelper.getInstance(requireContext().applicationContext)
                                .getTypeFace(it)
                        }
                    }

                    withContext(Dispatchers.Main) {
                        sectionTextView?.typeface =
                            weekendTypeface
                    }
                }


                sectionTextView?.text = displayable.getHeaderTitle()
                DateHelper.stringToDate(displayable.issueDate)?.let { date ->
                    headerView.findViewById<TextView>(R.id.issue_date)?.apply {
                        text = when {
                            issueStub?.isWeekend == true && issueStub.validityDate.isNullOrBlank() ->
                                // Regular Weekend Issue
                                DateHelper.dateToWeekendNotation(date)
                            issueStub?.isWeekend == true && issueStub.validityDate?.isNotBlank() == true ->
                                // Wochentaz Issue
                                DateHelper.dateToWeekNotation(date, issueStub.validityDate)
                            else ->
                                DateHelper.dateToLowerCaseString(date)
                        }
                    }
                }

                // On first section "die tageszeitung" or "wochentaz" the header should be bigger:
                if (displayable.getHeaderTitle() == getString(R.string.fragment_default_header_title)
                    || displayable.getHeaderTitle() == getString(R.string.fragment_default_week_header_title)
                ) {
                    val textPixelSize =
                        resources.getDimensionPixelSize(R.dimen.fragment_header_title_section_text_size)
                    val textSpSize =
                        resources.getDimension(R.dimen.fragment_header_title_section_text_size)
                    sectionTextView?.apply {
                        setTextSize(COMPLEX_UNIT_SP, textSpSize)
                        TextViewCompat.setAutoSizeTextTypeUniformWithConfiguration(
                            this,
                            TextViewCompat.getAutoSizeMinTextSize(this),
                            textPixelSize,
                            ceil(0.1 * resources.displayMetrics.density).toInt(),
                            TypedValue.COMPLEX_UNIT_PX
                        )
                        translationY = resources.getDimension(R.dimen.fragment_header_section_title_y_translation)
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