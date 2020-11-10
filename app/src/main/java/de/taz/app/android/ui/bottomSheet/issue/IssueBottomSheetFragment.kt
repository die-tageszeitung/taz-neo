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
import de.taz.app.android.R
import de.taz.app.android.api.ApiService
import de.taz.app.android.api.ConnectivityException
import de.taz.app.android.api.models.IssueStub
import de.taz.app.android.data.DataService
import de.taz.app.android.monkey.preventDismissal
import de.taz.app.android.download.DownloadService
import de.taz.app.android.persistence.repository.FileEntryRepository
import de.taz.app.android.persistence.repository.IssueRepository
import de.taz.app.android.simpleDateFormat
import de.taz.app.android.singletons.FileHelper
import de.taz.app.android.singletons.ToastHelper
import de.taz.app.android.ui.home.page.HomePageViewModel
import de.taz.app.android.ui.main.MainActivity
import de.taz.app.android.util.Log
import kotlinx.android.synthetic.main.fragment_bottom_sheet_issue.*
import kotlinx.android.synthetic.main.include_loading_screen.*
import kotlinx.coroutines.*
import java.lang.Exception
import java.lang.ref.WeakReference

class IssueBottomSheetFragment : BottomSheetDialogFragment() {

    private val log by Log
    private var issueStub: IssueStub? = null
    private var weakActivityReference: WeakReference<MainActivity>? = null

    private lateinit var apiService: ApiService
    private lateinit var fileEntryRepository: FileEntryRepository
    private lateinit var fileHelper: FileHelper
    private lateinit var downloadService: DownloadService
    private lateinit var issueRepository: IssueRepository
    private lateinit var dataService: DataService
    private lateinit var toastHelper: ToastHelper

    private val homeViewModel: HomePageViewModel by activityViewModels()

    companion object {
        fun create(
            mainActivity: MainActivity,
            issueStub: IssueStub
        ): IssueBottomSheetFragment {
            val fragment = IssueBottomSheetFragment()
            fragment.weakActivityReference = WeakReference(mainActivity)
            fragment.issueStub = issueStub
            return fragment
        }
    }

    override fun onAttach(context: Context) {
        super.onAttach(context)
        apiService = ApiService.getInstance(context.applicationContext)
        fileEntryRepository = FileEntryRepository.getInstance(context.applicationContext)
        fileHelper = FileHelper.getInstance(context.applicationContext)
        downloadService = DownloadService.getInstance(context.applicationContext)
        issueRepository = IssueRepository.getInstance(context.applicationContext)
        dataService = DataService.getInstance(context.applicationContext)
        toastHelper = ToastHelper.getInstance(context.applicationContext)
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

        if (issueStub?.dateDownload == null) {
            fragment_bottom_sheet_issue_delete?.visibility = View.GONE
            fragment_bottom_sheet_issue_download?.visibility = View.VISIBLE
        } else {
            fragment_bottom_sheet_issue_delete?.visibility = View.VISIBLE
            fragment_bottom_sheet_issue_download?.visibility = View.GONE
        }

        fragment_bottom_sheet_issue_read?.setOnClickListener {
            issueStub?.let { issueStub ->
                weakActivityReference?.get()?.showIssue(issueStub)
            }
            dismiss()
        }

        fragment_bottom_sheet_issue_share?.setOnClickListener {
            issueStub?.let { issueStub ->
                CoroutineScope(Dispatchers.IO).launch {
                    val issue = issueRepository.getIssue(issueStub)
                    issue.moment.getMomentFileToShare().let { image ->
                        fileEntryRepository.get(
                            image.name
                        )?.let {
                            dataService.ensureDownloaded(it, issue.baseUrl)
                        }
                        fileHelper.getFile(image).let { imageAsFile ->
                            val applicationId = view.context.packageName
                            val imageUriNew = FileProvider.getUriForFile(
                                view.context,
                                "${applicationId}.contentProvider",
                                imageAsFile
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
                }
            }
            dismiss()
        }

        fragment_bottom_sheet_issue_delete?.setOnClickListener {
            issueStub?.let { issueStub ->
                preventDismissal()
                fragment_bottom_sheet_issue_read?.setOnClickListener(null)
                fragment_bottom_sheet_issue_share?.setOnClickListener(null)
                fragment_bottom_sheet_issue_delete?.setOnClickListener(null)

                loading_screen?.visibility = View.VISIBLE

                CoroutineScope(Dispatchers.IO).launch {
                    val issue = issueStub.getIssue()
                    dataService.ensureDeleted(issue)
                    withContext(Dispatchers.Main) {
                        dismiss()
                    }
                    try {
                        dataService.getIssue(
                            issue.issueKey,
                            allowCache = false,
                            retryOnFailure = true,
                            forceUpdate = true
                        )
                        homeViewModel.notifyMomentChanged(simpleDateFormat.parse(issue.issueKey.date)!!)
                    } catch (e: ConnectivityException.Recoverable) {
                        log.warn("Redownloading after delete not possible as no internet connection is available")
                    }
                }
            }

        }
        fragment_bottom_sheet_issue_download?.setOnClickListener {
            CoroutineScope(Dispatchers.IO).launch {
                issueStub?.let { issueStub ->
                    // we refresh the issue from network, as the cache might be pretty stale at this point (issues might be edited after release)
                    issueRepository.getIssue(issueStub).let { issue ->
                        try {
                            val updatedIssue =
                                dataService.getIssue(
                                    issue.issueKey,
                                    allowCache = false
                                )
                            updatedIssue?.let { dataService.ensureDownloaded(it) }
                        } catch (e: ConnectivityException.Recoverable) {
                            toastHelper.showNoConnectionToast()
                        }
                    }
                }
            }
            dismiss()
        }
    }

}