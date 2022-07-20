package de.taz.app.android.ui.pdfViewer

import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.fragment.app.activityViewModels
import com.bumptech.glide.Glide
import de.taz.app.android.R
import de.taz.app.android.singletons.DateHelper
import de.taz.app.android.singletons.StorageService

class DrawerBodyPdfWithSectionsFragment : Fragment() {

    private lateinit var currentPageImageView: ImageView
    private lateinit var currentPageTitleView: TextView
    private lateinit var issueTitleTextView: TextView

    private lateinit var storageService: StorageService

    private val pdfPagerViewModel: PdfPagerViewModel by activityViewModels()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        storageService = StorageService.getInstance(requireContext().applicationContext)
        val view =
            inflater.inflate(R.layout.fragment_drawer_body_pdf_with_sections, container, false)

        currentPageImageView =
            view.findViewById(R.id.fragment_drawer_body_pdf_with_sections_current_page_image)
        currentPageTitleView =
            view.findViewById(R.id.fragment_drawer_body_pdf_with_sections_current_page_title)
        issueTitleTextView = view.findViewById(R.id.fragment_drawer_body_pdf_with_sections_title)

        return view
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        pdfPagerViewModel.currentPage.observe(viewLifecycleOwner) {
            pdfPagerViewModel.currentItem.value?.let { _ ->
                updateCurrentPageUI()
            }
        }
        pdfPagerViewModel.issuePublication.observe(viewLifecycleOwner) { issue_publication ->
            val date = DateHelper.stringToDate(issue_publication.date)
            val dateString = date?.let { DateHelper.dateToMonthYearString(it) }

            issueTitleTextView.text = getString(
                R.string.issue_title,
                dateString
            )
        }
    }

    private fun updateCurrentPageUI() {
//        TODO(peter) Adjust size for panorama pages
        Glide
            .with(this)
            .load(pdfPagerViewModel.currentPage.value
                ?.let {
                    storageService.getAbsolutePath(it.pagePdf)
                }).into(currentPageImageView)

        currentPageTitleView.text = pdfPagerViewModel.currentPage.value?.type?.ordinal?.let {
            resources.getQuantityString(
                R.plurals.pages,
                it,
                pdfPagerViewModel.currentPage.value?.pagina
            )
        }
    }
}