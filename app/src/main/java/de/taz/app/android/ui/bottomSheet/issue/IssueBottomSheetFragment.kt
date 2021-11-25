package de.taz.app.android.ui.bottomSheet.issue

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.FileProvider
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import de.taz.app.android.R
import de.taz.app.android.api.ApiService
import de.taz.app.android.api.models.Issue
import de.taz.app.android.content.ContentService
import de.taz.app.android.content.cache.CacheOperationFailedException
import de.taz.app.android.content.cache.CacheState
import de.taz.app.android.monkey.preventDismissal
import de.taz.app.android.persistence.repository.*
import de.taz.app.android.simpleDateFormat
import de.taz.app.android.singletons.AuthHelper
import de.taz.app.android.singletons.StorageService
import de.taz.app.android.singletons.ToastHelper
import de.taz.app.android.ui.pdfViewer.PdfPagerActivity
import de.taz.app.android.ui.home.page.IssueFeedViewModel
import de.taz.app.android.ui.issueViewer.IssueViewerActivity
import de.taz.app.android.util.Log
import kotlinx.android.synthetic.main.fragment_bottom_sheet_issue.*
import kotlinx.android.synthetic.main.include_loading_screen.*
import kotlinx.coroutines.*
import java.io.File


private const val KEY_ISSUE_PUBLICATION = "KEY_ISSUE_PUBLICATION"

class IssueBottomSheetFragment : BottomSheetDialogFragment() {

    private val log by Log
    private lateinit var issuePublication: IssuePublication

    private lateinit var apiService: ApiService
    private lateinit var fileEntryRepository: FileEntryRepository
    private lateinit var storageService: StorageService
    private lateinit var contentService: ContentService
    private lateinit var issueRepository: IssueRepository
    private lateinit var toastHelper: ToastHelper
    private lateinit var authHelper: AuthHelper


    private val homeViewModel: IssueFeedViewModel by activityViewModels()

    companion object {
        fun create(
            issuePublication: AbstractIssuePublication
        ): IssueBottomSheetFragment {
            val args = Bundle()
            args.putParcelable(KEY_ISSUE_PUBLICATION, issuePublication)
            return IssueBottomSheetFragment().apply {
                arguments = args
            }
        }
    }

    override fun onAttach(context: Context) {
        super.onAttach(context)
        apiService = ApiService.getInstance(context.applicationContext)
        fileEntryRepository = FileEntryRepository.getInstance(context.applicationContext)
        storageService = StorageService.getInstance(context.applicationContext)
        authHelper = AuthHelper.getInstance(context.applicationContext)
        contentService = ContentService.getInstance(context.applicationContext)
        issueRepository = IssueRepository.getInstance(context.applicationContext)
        toastHelper = ToastHelper.getInstance(context.applicationContext)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        issuePublication = requireArguments().getParcelable(KEY_ISSUE_PUBLICATION)!!
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return layoutInflater.inflate(R.layout.fragment_bottom_sheet_issue, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        loading_screen?.visibility = View.VISIBLE
        lifecycleScope.launch {
            if (getIsDownloaded()) {
                fragment_bottom_sheet_issue_delete?.visibility = View.VISIBLE
                fragment_bottom_sheet_issue_download?.visibility = View.GONE
            } else {
                fragment_bottom_sheet_issue_delete?.visibility = View.GONE
                fragment_bottom_sheet_issue_download?.visibility = View.VISIBLE
            }
            loading_screen?.visibility = View.GONE
        }

        fragment_bottom_sheet_issue_read?.setOnClickListener {
            lifecycleScope.launch {
                if (homeViewModel.getPdfMode()) {
                    Intent(requireActivity(), PdfPagerActivity::class.java).apply {
                        putExtra(
                            PdfPagerActivity.KEY_ISSUE_PUBLICATION,
                            IssuePublicationWithPages(issuePublication)
                        )
                        startActivity(this)
                    }
                } else {

                    Intent(requireActivity(), IssueViewerActivity::class.java).apply {
                        putExtra(IssueViewerActivity.KEY_ISSUE_PUBLICATION, issuePublication)
                        startActivityForResult(this, 0)
                    }
                }
            }

            dismiss()
        }

        fragment_bottom_sheet_issue_share?.setOnClickListener {
            CoroutineScope(Dispatchers.IO).launch {
                var issue = contentService.downloadMetadata(issuePublication) as Issue
                var image = issue.moment.getMomentFileToShare()
                fileEntryRepository.get(
                    image.name
                )?.let {
                    contentService.downloadSingleFileIfNotDownloaded(it, issue.baseUrl)
                }
                // refresh issue after altering file state
                issue = contentService.downloadMetadata(issuePublication) as Issue
                image = issue.moment.getMomentFileToShare()

                storageService.getAbsolutePath(image)?.let { imageAsFile ->
                    val applicationId = view.context.packageName
                    val imageUriNew = FileProvider.getUriForFile(
                        view.context,
                        "${applicationId}.contentProvider",
                        File(imageAsFile)
                    )

                    log.debug("imageUriNew: $imageUriNew")
                    log.debug("imageAsFile: $imageAsFile")
                    val sendIntent: Intent = Intent().apply {
                        action = Intent.ACTION_SEND
                        putExtra(Intent.EXTRA_STREAM, imageUriNew)
                        type = "image/jpg"
                    }
                    val shareIntent = Intent.createChooser(sendIntent, null)
                    view.context.startActivity(shareIntent)
                }
            }
            dismiss()
        }

        fragment_bottom_sheet_issue_delete?.setOnClickListener {
            preventDismissal()
            fragment_bottom_sheet_issue_read?.setOnClickListener(null)
            fragment_bottom_sheet_issue_share?.setOnClickListener(null)
            fragment_bottom_sheet_issue_delete?.setOnClickListener(null)

            loading_screen?.visibility = View.VISIBLE
            val viewModel = ::homeViewModel.get()

            CoroutineScope(Dispatchers.IO).launch {
                contentService.deleteIssue(issuePublication)
                viewModel.notifyMomentChanged(simpleDateFormat.parse(issuePublication.date)!!)
                withContext(Dispatchers.Main) {
                    dismiss()
                }
            }
        }

        fragment_bottom_sheet_issue_download?.setOnClickListener {
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    contentService.downloadToCache(issuePublication)
                } catch (e: CacheOperationFailedException) {
                    // Errors are handled in CoverViewBinding
                }
            }
            dismiss()
        }
    }

    override fun onStart() {
        super.onStart()
        //this forces the sheet to appear at max height even on landscape
        val behavior = BottomSheetBehavior.from(requireView().parent as View)
        behavior.state = BottomSheetBehavior.STATE_EXPANDED
    }

    private suspend fun getIsDownloaded(): Boolean {
        return contentService.getCacheState(
            issuePublication
        ).cacheState == CacheState.PRESENT
    }
}