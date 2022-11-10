package de.taz.app.android.ui.bottomSheet.issue

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.core.content.FileProvider
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import com.google.android.material.bottomsheet.BottomSheetBehavior
import de.taz.app.android.R
import de.taz.app.android.api.ApiService
import de.taz.app.android.api.models.Issue
import de.taz.app.android.base.ViewBindingBottomSheetFragment
import de.taz.app.android.content.ContentService
import de.taz.app.android.content.cache.CacheOperationFailedException
import de.taz.app.android.content.cache.CacheState
import de.taz.app.android.databinding.FragmentBottomSheetIssueBinding
import de.taz.app.android.monkey.preventDismissal
import de.taz.app.android.persistence.repository.*
import de.taz.app.android.simpleDateFormat
import de.taz.app.android.singletons.AuthHelper
import de.taz.app.android.singletons.CannotDetermineBaseUrlException
import de.taz.app.android.singletons.StorageService
import de.taz.app.android.singletons.ToastHelper
import de.taz.app.android.ui.home.page.IssueFeedViewModel
import de.taz.app.android.ui.issueViewer.IssueViewerActivity
import de.taz.app.android.ui.pdfViewer.PdfPagerActivity
import de.taz.app.android.util.Log
import io.sentry.Sentry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File


private const val KEY_ISSUE_PUBLICATION = "KEY_ISSUE_PUBLICATION"

class IssueBottomSheetFragment : ViewBindingBottomSheetFragment<FragmentBottomSheetIssueBinding>() {

    private val log by Log
    private lateinit var issuePublication: IssuePublication

    private lateinit var apiService: ApiService
    private lateinit var fileEntryRepository: FileEntryRepository
    private lateinit var storageService: StorageService
    private lateinit var contentService: ContentService
    private lateinit var issueRepository: IssueRepository
    private lateinit var toastHelper: ToastHelper
    private lateinit var authHelper: AuthHelper

    private val loadingScreen by lazy { view?.findViewById<View>(R.id.loading_screen) }

    private val homeViewModel: IssueFeedViewModel by activityViewModels()

    companion object {
        fun newInstance(
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

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        loadingScreen?.visibility = View.VISIBLE
        lifecycleScope.launch {
            if (getIsDownloaded()) {
                viewBinding.fragmentBottomSheetIssueDelete.visibility = View.VISIBLE
                viewBinding.fragmentBottomSheetIssueDownload.visibility = View.GONE
            } else {
                viewBinding.fragmentBottomSheetIssueDelete.visibility = View.GONE
                viewBinding.fragmentBottomSheetIssueDownload.visibility = View.VISIBLE
            }
            loadingScreen?.visibility = View.GONE
        }

        viewBinding.fragmentBottomSheetIssueRead.setOnClickListener {
            requireActivity().apply {
                val intent =
                    if (homeViewModel.getPdfMode()) {
                        PdfPagerActivity.newIntent(this, IssuePublicationWithPages(issuePublication))
                    } else {
                        IssueViewerActivity.newIntent(this, issuePublication)
                    }
                startActivity(intent)
            }
            dismiss()
        }

        viewBinding.fragmentBottomSheetIssueShare.setOnClickListener {
            lifecycleScope.launch {
                loadingScreen?.visibility = View.VISIBLE

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
                loadingScreen?.visibility = View.GONE
                dismiss()
            }
        }

        viewBinding.fragmentBottomSheetIssueDelete.setOnClickListener {
            preventDismissal()
            viewBinding.fragmentBottomSheetIssueRead.setOnClickListener(null)
            viewBinding.fragmentBottomSheetIssueShare.setOnClickListener(null)
            viewBinding.fragmentBottomSheetIssueDelete.setOnClickListener(null)

            loadingScreen?.visibility = View.VISIBLE
            val viewModel = ::homeViewModel.get()

            applicationScope.launch {
                contentService.deleteIssue(issuePublication)
                viewModel.notifyMomentChanged(simpleDateFormat.parse(issuePublication.date)!!)
                withContext(Dispatchers.Main) {
                    dismiss()
                }
            }
        }

        viewBinding.fragmentBottomSheetIssueDownload.setOnClickListener {
            applicationScope.launch {
                try {
                    contentService.downloadIssuePublicationToCache(issuePublication)
                } catch (e: CacheOperationFailedException) {
                    // Errors are handled in CoverViewBinding
                } catch (e: CannotDetermineBaseUrlException) {
                    // FIXME (johannes): Workaround to #14367
                    // concurrent download/deletion jobs might result in a articles missing their parent issue and thus not being able to find the base url
                    val hint = "Could not determine baseurl for issue with publication $issuePublication"
                    log.error(hint, e)
                    Sentry.captureException(e, hint)
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