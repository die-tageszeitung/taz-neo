package de.taz.app.android.ui.bottomSheet.issue

import android.content.Context
import android.os.Bundle
import android.view.View
import androidx.core.app.ShareCompat
import androidx.core.content.FileProvider
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.commit
import androidx.lifecycle.flowWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.google.android.material.bottomsheet.BottomSheetBehavior
import de.taz.app.android.R
import de.taz.app.android.api.ApiService
import de.taz.app.android.api.models.Issue
import de.taz.app.android.base.ViewBindingBottomSheetFragment
import de.taz.app.android.content.ContentService
import de.taz.app.android.content.cache.CacheOperationFailedException
import de.taz.app.android.dataStore.GeneralDataStore
import de.taz.app.android.databinding.FragmentBottomSheetIssueBinding
import de.taz.app.android.persistence.repository.AbstractIssuePublication
import de.taz.app.android.persistence.repository.FileEntryRepository
import de.taz.app.android.persistence.repository.IssuePublication
import de.taz.app.android.persistence.repository.IssuePublicationWithPages
import de.taz.app.android.persistence.repository.IssueRepository
import de.taz.app.android.persistence.repository.MomentRepository
import de.taz.app.android.sentry.SentryWrapper
import de.taz.app.android.simpleDateFormat
import de.taz.app.android.singletons.AuthHelper
import de.taz.app.android.singletons.CannotDetermineBaseUrlException
import de.taz.app.android.singletons.ConnectionStatusHelper
import de.taz.app.android.singletons.StorageService
import de.taz.app.android.singletons.ToastHelper
import de.taz.app.android.tracking.Tracker
import de.taz.app.android.ui.home.page.IssueFeedViewModel
import de.taz.app.android.ui.issueViewer.IssueViewerWrapperFragment
import de.taz.app.android.ui.pdfViewer.PdfPagerWrapperFragment
import de.taz.app.android.ui.showNoInternetDialog
import de.taz.app.android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File


private const val KEY_ISSUE_PUBLICATION = "KEY_ISSUE_PUBLICATION"

class IssueBottomSheetFragment : ViewBindingBottomSheetFragment<FragmentBottomSheetIssueBinding>() {

    private val log by Log
    private lateinit var issuePublication: IssuePublication
    private lateinit var isDownloadedFlow: Flow<Boolean>

    private lateinit var apiService: ApiService
    private lateinit var fileEntryRepository: FileEntryRepository
    private lateinit var storageService: StorageService
    private lateinit var contentService: ContentService
    private lateinit var issueRepository: IssueRepository
    private lateinit var momentRepository: MomentRepository
    private lateinit var toastHelper: ToastHelper
    private lateinit var authHelper: AuthHelper
    private lateinit var tracker: Tracker
    private lateinit var generalDataStore: GeneralDataStore

    private val loadingScreen by lazy { view?.findViewById<View>(R.id.loading_screen) }

    private val homeViewModel: IssueFeedViewModel by activityViewModels()

    companion object {
        const val TAG = "IssueBottomSheetFragment"

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
        momentRepository = MomentRepository.getInstance(context.applicationContext)
        toastHelper = ToastHelper.getInstance(context.applicationContext)
        tracker = Tracker.getInstance(context.applicationContext)
        generalDataStore = GeneralDataStore.getInstance(context.applicationContext)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        issuePublication = requireArguments().getParcelable(KEY_ISSUE_PUBLICATION)!!

        isDownloadedFlow = homeViewModel.pdfModeFlow.map { isPdf ->
            val abstractIssuePublication = if (isPdf) {
                IssuePublicationWithPages(issuePublication)
            } else {
                issuePublication
            }

            return@map contentService.isPresent(abstractIssuePublication)
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        loadingScreen?.visibility = View.VISIBLE

        isDownloadedFlow
            .flowWithLifecycle(lifecycle)
            .onEach { isDownloaded ->
                if (isDownloaded) {
                    viewBinding.fragmentBottomSheetIssueDelete.visibility = View.VISIBLE
                    viewBinding.fragmentBottomSheetIssueDownload.visibility = View.GONE
                    viewBinding.fragmentBottomSheetIssueDownloadAudios.setText(R.string.fragment_bottom_sheet_issue_download_additionally_audios)
                } else {
                    viewBinding.fragmentBottomSheetIssueDelete.visibility = View.GONE
                    viewBinding.fragmentBottomSheetIssueDownload.visibility = View.VISIBLE
                    viewBinding.fragmentBottomSheetIssueDownloadAudios.setText(R.string.fragment_bottom_sheet_issue_download_with_audios)
                }
            }.launchIn(lifecycleScope)

        lifecycleScope.launch {
            if (issueRepository.areAllAudiosDownloaded(issuePublication)) {
                viewBinding.fragmentBottomSheetIssueDownloadAudios.visibility = View.GONE
            }
            loadingScreen?.visibility = View.GONE
        }

        viewBinding.fragmentBottomSheetIssueRead.setOnClickListener {
            applicationScope.launch {
                handleIssueRead()
            }
            dismiss()
        }

        viewBinding.fragmentBottomSheetIssueShare.setOnClickListener {
            lifecycleScope.launch {
                shareIssue(issuePublication)
            }
        }

        viewBinding.fragmentBottomSheetIssueDelete.setOnClickListener {
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
                downloadIssue()
            }
            dismiss()
        }

        viewBinding.fragmentBottomSheetIssueDownloadAudios.setOnClickListener {
            loadingScreen?.visibility = View.VISIBLE

            applicationScope.launch {
                if (!isDownloadedFlow.first()) {
                    downloadIssue()
                }
                val issueStub = checkNotNull(
                    issueRepository.getMostValuableIssueStubForPublication(issuePublication)
                )
                contentService.downloadAllAudiosFromIssuePublication(
                    issuePublication,
                    issueStub.baseUrl
                )
                tracker.trackIssueDownloadAudiosEvent(issueStub.issueKey)
                withContext(Dispatchers.Main) {
                    dismiss()
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        //this forces the sheet to appear at max height even on landscape
        val behavior = BottomSheetBehavior.from(requireView().parent as View)
        behavior.state = BottomSheetBehavior.STATE_EXPANDED
    }

    override fun onResume() {
        super.onResume()
        tracker.trackIssueActionsDialog()
    }


    /**
     * Check if we are in pdf mode, if we got internet connection or if we already have downloaded
     * the issue.
     * Show a dialog if we have no internet connection and want to read an absent issue.
     */
    private suspend fun handleIssueRead() {
        val isOnline = ConnectionStatusHelper.isOnline(requireContext())
        requireActivity().apply {
            val fragment =
                if (homeViewModel.getPdfMode()) {
                    PdfPagerWrapperFragment.newInstance(IssuePublicationWithPages(issuePublication))
                } else {
                    IssueViewerWrapperFragment.newInstance(issuePublication)
                }
            if (isDownloadedFlow.first() || isOnline) {
                supportFragmentManager.commit {
                    add(R.id.main_content_fragment_placeholder, fragment)
                    addToBackStack(null)
                }
            } else {
                parentFragment?.showNoInternetDialog()
            }
        }
    }

    private suspend fun shareIssue(issuePublication: AbstractIssuePublication) {
        try {
            loadingScreen?.visibility = View.VISIBLE

            // Only download the full Issue from the database if it is not present
            if (!contentService.isPresent(issuePublication)) {
                contentService.downloadMetadata(issuePublication) as Issue
            }

            val issueStub =
                checkNotNull(issueRepository.getMostValuableIssueStubForPublication(issuePublication))
            var image =
                checkNotNull(momentRepository.get(issueStub.issueKey)).getMomentFileToShare()

            val imageFileEntry = checkNotNull(fileEntryRepository.get(image.name))
            contentService.downloadSingleFileIfNotDownloaded(imageFileEntry, issueStub.baseUrl)
            // refresh image after altering file state
            image = checkNotNull(momentRepository.get(issueStub.issueKey)).getMomentFileToShare()

            storageService.getAbsolutePath(image)?.let { imageAsFile ->
                val applicationId = requireContext().packageName
                val imageUriNew = FileProvider.getUriForFile(
                    requireContext(),
                    "${applicationId}.contentProvider",
                    File(imageAsFile)
                )

                tracker.trackShareMomentEvent(issueStub.issueKey)

                ShareCompat.IntentBuilder(requireContext())
                    .setType("image/jpg")
                    .setStream(imageUriNew)
                    .startChooser()
            }
            loadingScreen?.visibility = View.GONE
            dismiss()

        } catch (e: Exception) {
            log.error("Error while sharing $issuePublication", e)
            toastHelper.showToast(R.string.toast_unknown_error, long = true)
            dismiss()
        }
    }

    private suspend fun downloadIssue() {
        try {
            val isPdfMode = generalDataStore.pdfMode.get()
            val abstractIssuePublication = if (isPdfMode) {
                IssuePublicationWithPages(issuePublication)
            } else {
                issuePublication
            }
            contentService.downloadIssuePublicationToCache(abstractIssuePublication)
        } catch (e: CacheOperationFailedException) {
            // Errors are handled in CoverViewBinding
        } catch (e: CannotDetermineBaseUrlException) {
            // FIXME (johannes): Workaround to #14367
            // concurrent download/deletion jobs might result in a articles missing their parent issue and thus not being able to find the base url
            log.warn(
                "Could not determine baseurl for issue with publication $issuePublication",
                e
            )
            SentryWrapper.captureException(e)
        }
    }
}