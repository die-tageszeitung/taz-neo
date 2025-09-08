package de.taz.app.android.ui.bottomSheet

import android.content.Context
import android.content.DialogInterface
import android.os.Bundle
import android.view.View
import androidx.core.os.bundleOf
import androidx.core.view.updatePadding
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import com.google.android.material.bottomsheet.BottomSheetBehavior
import de.taz.app.android.R
import de.taz.app.android.base.ViewBindingBottomSheetFragment
import de.taz.app.android.dataStore.GeneralDataStore
import de.taz.app.android.databinding.FragmentContinueReadBottomSheetBinding
import de.taz.app.android.monkey.setBehaviorStateOnLandscape
import de.taz.app.android.persistence.repository.ArticleRepository
import de.taz.app.android.persistence.repository.PageRepository
import de.taz.app.android.persistence.repository.SectionRepository
import de.taz.app.android.singletons.StorageService
import de.taz.app.android.ui.issueViewer.IssueKeyWithDisplayableKey
import de.taz.app.android.ui.issueViewer.IssueViewerViewModel
import de.taz.app.android.ui.pdfViewer.PdfPagerViewModel
import de.taz.app.android.util.Log
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

const val SHOW_CONTINUE_READ_THE_SAME_NOT_MORE_THAN = 5

class ContinueReadBottomSheetFragment :
    ViewBindingBottomSheetFragment<FragmentContinueReadBottomSheetBinding>() {


    private val issueContentViewModel: IssueViewerViewModel by activityViewModels()
    private val pdfPagerViewModel: PdfPagerViewModel by viewModels({requireParentFragment()})
    private lateinit var articleRepository: ArticleRepository
    private lateinit var generalDataStore: GeneralDataStore
    private lateinit var pageRepository: PageRepository
    private lateinit var sectionRepository: SectionRepository
    private lateinit var storageService: StorageService

    private var isArticle = false
    private var isPage = false

    private val continueReadDisplayable: IssueKeyWithDisplayableKey
        get() = arguments?.getParcelable(ARGUMENT_DISPLAYABLE_KEY)
            ?: throw IllegalStateException("ContinueReadBottomSheetFragment needs a valid displayableKey")

    private var continueReadClicked = false
    private val log by Log

    companion object {
        const val TAG = "ContinueReadModeBottomSheet"
        private const val ARGUMENT_DISPLAYABLE_KEY = "ARGUMENT_DISPLAYABLE_KEY"

        fun newInstance(continueReadDisplayable: IssueKeyWithDisplayableKey) =
            ContinueReadBottomSheetFragment().apply {
                arguments = bundleOf(
                    ARGUMENT_DISPLAYABLE_KEY to continueReadDisplayable
                )
            }
    }

    override fun onAttach(context: Context) {
        super.onAttach(context)
        articleRepository = ArticleRepository.getInstance(context.applicationContext)
        generalDataStore = GeneralDataStore.getInstance(context.applicationContext)
        sectionRepository = SectionRepository.getInstance(context.applicationContext)
        pageRepository = PageRepository.getInstance(context.applicationContext)
        storageService = StorageService.getInstance(context.applicationContext)
    }

    override fun onStart() {
        super.onStart()
        setBehaviorStateOnLandscape(BottomSheetBehavior.STATE_EXPANDED)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        isArticle = continueReadDisplayable.displayableKey.startsWith("art")
        val isSection = continueReadDisplayable.displayableKey.startsWith("sec")
        isPage = continueReadDisplayable.displayableKey.startsWith("s") && continueReadDisplayable.displayableKey.endsWith(".pdf")

        lifecycleScope.launch {
            if (isArticle) {
                val article = articleRepository.get(continueReadDisplayable.displayableKey)
                viewBinding.pagePreviewImage.visibility = View.GONE
                if (article != null) {
                    viewBinding.continueReadDisplayable.text = article.title

                    if (article.imageList.isNotEmpty()) {
                        storageService.getAbsolutePath(article.imageList.first())?.let {
                            viewBinding.articlePreviewImage.loadImageFileWithGlide(it)
                        }
                    } else {
                        applyExtraPaddingWhenNoPreviewImage()
                    }
                }
            } else if (isSection) {
                val section = sectionRepository.get(continueReadDisplayable.displayableKey)
                viewBinding.pagePreviewImage.visibility = View.GONE
                if (section != null) {
                    viewBinding.continueReadDisplayable.text = section.title

                    if (section.imageList.isNotEmpty()) {
                        storageService.getAbsolutePath(section.imageList.first())?.let {
                            viewBinding.articlePreviewImage.loadImageFileWithGlide(it)
                        }
                    } else {
                        applyExtraPaddingWhenNoPreviewImage()
                    }
                }
            } else if (isPage) {
                viewBinding.continueReadDisplayable.text = continueReadDisplayable.displayableKey
                val page = pageRepository.get(continueReadDisplayable.displayableKey)

                if (page != null) {
                    viewBinding.continueReadDisplayable.text = "Seite ${page.pagina}: ${page.title}"
                    viewBinding.articlePreviewImage.visibility = View.GONE
                    viewBinding.pagePreviewImage.visibility = View.VISIBLE
                    storageService.getAbsolutePath(page.pagePdf)?.let {
                        viewBinding.pagePreviewImage.loadImageFileWithGlide(it)
                    }
                } else {
                    applyExtraPaddingWhenNoPreviewImage()
                }
            }
        }

        viewBinding.continueReadLayout.setOnClickListener {
            lifecycleScope.launch {
                if (generalDataStore.pdfMode.get()) {
                    if (isArticle) {
                        pdfPagerViewModel.showArticle(
                            continueReadDisplayable.displayableKey,
                            continueReadDisplayable.issueKey
                        )
                    } else {
                        pdfPagerViewModel.goToPdfPage(continueReadDisplayable.displayableKey)
                    }
                } else {
                    issueContentViewModel.setDisplayable(continueReadDisplayable)
                }
                continueReadClicked = true
                dismiss()
            }
        }
        viewBinding.buttonClose.setOnClickListener { dismiss() }
    }

    override fun onDismiss(dialog: DialogInterface) {
        lifecycleScope.launch {
            withContext(NonCancellable) {
                if (continueReadClicked) {
                    generalDataStore.continueReadClicked.set(
                        generalDataStore.continueReadClicked.get() + 1
                    )
                    generalDataStore.continueReadDismissed.set(0)
                } else {
                    generalDataStore.continueReadDismissed.set(
                        generalDataStore.continueReadDismissed.get() + 1
                    )
                    generalDataStore.continueReadClicked.set(0)
                }
                log.verbose("times dismissed: ${generalDataStore.continueReadDismissed.get()}")
                log.verbose("times clicked: ${generalDataStore.continueReadClicked.get()}")
                super.onDismiss(dialog)
            }
        }
    }

    private fun applyExtraPaddingWhenNoPreviewImage() {
        viewBinding.articlePreviewImage.visibility = View.GONE
        val padding = resources.getDimensionPixelSize(R.dimen.bottom_sheet_continue_read_margins)
        viewBinding.continueReadTitle.updatePadding(padding, 0, 0 ,0)
        viewBinding.continueReadDisplayable.updatePadding(padding, 0, 0 ,0)
    }
}