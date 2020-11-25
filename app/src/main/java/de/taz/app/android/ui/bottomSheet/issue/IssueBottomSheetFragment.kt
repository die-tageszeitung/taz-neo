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
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import de.taz.app.android.ISSUE_KEY
import de.taz.app.android.R
import de.taz.app.android.api.ApiService
import de.taz.app.android.api.ConnectivityException
import de.taz.app.android.api.models.IssueStatus
import de.taz.app.android.data.DataService
import de.taz.app.android.download.DownloadService
import de.taz.app.android.monkey.preventDismissal
import de.taz.app.android.persistence.repository.FileEntryRepository
import de.taz.app.android.persistence.repository.IssueKey
import de.taz.app.android.persistence.repository.IssuePublication
import de.taz.app.android.persistence.repository.IssueRepository
import de.taz.app.android.simpleDateFormat
import de.taz.app.android.singletons.StorageService
import de.taz.app.android.singletons.ToastHelper
import de.taz.app.android.ui.pdfViewer.PdfPagerActivity
import de.taz.app.android.ui.home.page.HomePageViewModel
import de.taz.app.android.ui.issueViewer.IssueViewerActivity
import de.taz.app.android.ui.main.MainActivity.Companion.KEY_ISSUE_KEY
import de.taz.app.android.util.Log
import kotlinx.android.synthetic.main.fragment_bottom_sheet_issue.*
import kotlinx.android.synthetic.main.include_loading_screen.*
import kotlinx.coroutines.*
import java.io.File


private const val KEY_ISSUE_PUBLICATION = "KEY_ISSUE_PUBLICATION"
private const val KEY_IS_DOWNLOADED = "KEY_IS_DOWNLOADED"

class IssueBottomSheetFragment : BottomSheetDialogFragment() {

    private val log by Log
    private lateinit var issueKey: IssueKey

    private lateinit var apiService: ApiService
    private lateinit var fileEntryRepository: FileEntryRepository
    private lateinit var fileHelper: StorageService
    private lateinit var downloadService: DownloadService
    private lateinit var issueRepository: IssueRepository
    private lateinit var dataService: DataService
    private lateinit var toastHelper: ToastHelper

    private var isDownloaded: Boolean = false

    private val homeViewModel: HomePageViewModel by activityViewModels()

    companion object {
        fun create(
            issueKey: IssueKey,
            isDownloaded: Boolean
        ): IssueBottomSheetFragment {
            val args = Bundle()
            args.putParcelable(KEY_ISSUE_PUBLICATION, issueKey)
            args.putBoolean(KEY_IS_DOWNLOADED, isDownloaded)
            return IssueBottomSheetFragment().apply {
                arguments = args
            }
        }
    }

    override fun onAttach(context: Context) {
        super.onAttach(context)
        apiService = ApiService.getInstance(context.applicationContext)
        fileEntryRepository = FileEntryRepository.getInstance(context.applicationContext)
        fileHelper = StorageService.getInstance(context.applicationContext)
        downloadService = DownloadService.getInstance(context.applicationContext)
        issueRepository = IssueRepository.getInstance(context.applicationContext)
        dataService = DataService.getInstance(context.applicationContext)
        toastHelper = ToastHelper.getInstance(context.applicationContext)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        issueKey = requireArguments().getParcelable(KEY_ISSUE_PUBLICATION)!!
        isDownloaded = requireArguments().getBoolean(KEY_IS_DOWNLOADED, false)

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

        if (!isDownloaded) {
            fragment_bottom_sheet_issue_delete?.visibility = View.GONE
            fragment_bottom_sheet_issue_download?.visibility = View.VISIBLE
        } else {
            fragment_bottom_sheet_issue_delete?.visibility = View.VISIBLE
            fragment_bottom_sheet_issue_download?.visibility = View.GONE
            if (issueKey.status == IssueStatus.regular) {
                fragment_bottom_sheet_issue_read_pdf?.visibility = View.VISIBLE
            }
        }

        fragment_bottom_sheet_issue_read?.setOnClickListener {
            lifecycleScope.launch {
                withContext(Dispatchers.IO) {
                    Intent(requireActivity(), IssueViewerActivity::class.java).apply {
                        putExtra(IssueViewerActivity.KEY_ISSUE_KEY, issueKey)
                        startActivityForResult(this, 0)
                    }
                    dismiss()
                }
            }
        }

        fragment_bottom_sheet_issue_read_pdf?.setOnClickListener {
            Intent(requireActivity(), PdfPagerActivity::class.java).apply {
                putExtra(KEY_ISSUE_KEY, issueKey)
                startActivity(this)
            }
            dismiss()
        }
        
        fragment_bottom_sheet_issue_share?.setOnClickListener {
            CoroutineScope(Dispatchers.IO).launch {
                var issue = dataService.getIssue(IssuePublication(issueKey))
                var image = issue.moment.getMomentFileToShare()
                fileEntryRepository.get(
                    image.name
                )?.let {
                    dataService.ensureDownloaded(it, issue.baseUrl)
                }
                // refresh issue after altering file state
                issue = dataService.getIssue(IssuePublication(issueKey))
                image = issue.moment.getMomentFileToShare()

                fileHelper.getAbsolutePath(image)?.let { imageAsFile ->
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
                val issue = dataService.getIssue(IssuePublication(issueKey))
                issue?.let {
                    dataService.ensureDeletedFiles(it)
                    withContext(Dispatchers.Main) {
                        dismiss()
                    }
                    try {
                        dataService.getIssue(
                            IssuePublication(issue.issueKey),
                            allowCache = false,
                            retryOnFailure = true,
                            forceUpdate = true
                        )

                        viewModel.notifyMomentChanged(simpleDateFormat.parse(issue.issueKey.date)!!)
                    } catch (e: ConnectivityException.Recoverable) {
                        log.warn("Redownloading after delete not possible as no internet connection is available")
                    }
                }
            }


        }
        fragment_bottom_sheet_issue_download?.setOnClickListener {
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val issue =
                        dataService.getIssue(
                            IssuePublication(issueKey)
                        )
                    dataService.ensureDownloaded(issue)
                } catch (e: ConnectivityException.Recoverable) {
                    toastHelper.showNoConnectionToast()
                }
            }
            dismiss()
        }
    }
}