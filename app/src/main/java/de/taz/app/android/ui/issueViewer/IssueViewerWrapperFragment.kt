package de.taz.app.android.ui.issueViewer

import android.content.Context
import android.os.Bundle
import android.view.View
import androidx.core.os.bundleOf
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.flowWithLifecycle
import androidx.lifecycle.lifecycleScope
import de.taz.app.android.METADATA_DOWNLOAD_RETRY_INDEFINITELY
import de.taz.app.android.R
import de.taz.app.android.api.models.Issue
import de.taz.app.android.content.ContentService
import de.taz.app.android.content.cache.CacheOperationFailedException
import de.taz.app.android.dataStore.GeneralDataStore
import de.taz.app.android.persistence.repository.IssueKey
import de.taz.app.android.persistence.repository.IssuePublication
import de.taz.app.android.singletons.AuthHelper
import de.taz.app.android.singletons.ToastHelper
import de.taz.app.android.ui.SuccessfulLoginAction
import de.taz.app.android.ui.TazViewerFragment
import de.taz.app.android.ui.bottomSheet.ContinueReadBottomSheetFragment
import de.taz.app.android.ui.bottomSheet.SHOW_CONTINUE_READ_THE_SAME_NOT_MORE_THAN
import de.taz.app.android.ui.login.fragments.SubscriptionElapsedBottomSheetFragment
import de.taz.app.android.ui.main.MainActivity
import de.taz.app.android.ui.showAlwaysTitleSectionSettingDialog
import de.taz.app.android.ui.showContinueReadSettingDialog
import de.taz.app.android.util.showIssueDownloadFailedDialog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlin.reflect.KClass

/**
 * Fragment to show an issue with sections and articles in a Pager.
 *
 * This class takes care of getting the metadata from the backend and handing the information to the
 * [IssueViewerViewModel]
 *
 * Additionally this activity shows a dialog if downloading the metadata fails - this error will be
 * triggered by other fragments as well (e.g. by [de.taz.app.android.ui.webview.WebViewFragment]
 * We want to have this error handling in one position to ensure we do not show the dialog more
 * often then necessary (once)
 *
 * If a user logs in via an [de.taz.app.android.ui.login.fragments.ArticleLoginBottomSheetFragment] this
 * activity gets an activityResult and will restart with the new issue.
 *
 * This fragment downloads the given [IssuePublication] and uses an [IssueViewerFragment] to then
 * show the displayable
 *
 * TODO Hopefully we can merge this with the [IssueViewerFragment]
 */
class IssueViewerWrapperFragment : TazViewerFragment(), SuccessfulLoginAction {

    val issuePublication: IssuePublication
        get() = requireNotNull(arguments?.getParcelable(KEY_ISSUE_PUBLICATION)) {
            "IssueViewerWrapperFragment needs to be started with KEY_ISSUE_KEY in Intent extras of type IssueKey"
        }
    private val displayableKey: String?
        get() = arguments?.getString(KEY_DISPLAYABLE)

    private lateinit var contentService: ContentService
    private lateinit var authHelper: AuthHelper
    private lateinit var toastHelper: ToastHelper
    private lateinit var generalDataStore: GeneralDataStore

    private val issueViewerViewModel: IssueViewerViewModel by activityViewModels()

    override val fragmentClass: KClass<IssueViewerFragment> = IssueViewerFragment::class

    companion object {
        const val KEY_ISSUE_PUBLICATION = "KEY_ISSUE_PUBLICATION"
        const val KEY_DISPLAYABLE = "KEY_DISPLAYABLE"

        fun newInstance(
            issuePublication: IssuePublication,
            displayableKey: String? = null
        ) = IssueViewerWrapperFragment().apply {
            arguments = bundleOf(
                KEY_ISSUE_PUBLICATION to issuePublication,
                KEY_DISPLAYABLE to displayableKey
            )
        }
    }

    override fun onAttach(context: Context) {
        super.onAttach(context)
        contentService = ContentService.getInstance(context.applicationContext)
        authHelper = AuthHelper.getInstance(context.applicationContext)
        generalDataStore = GeneralDataStore.getInstance(context.applicationContext)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (savedInstanceState == null) {
            lifecycleScope.launch(Dispatchers.Default) {

                val issuePublication = this@IssueViewerWrapperFragment.issuePublication
                val cachedIssueKey = contentService.getIssueKey(issuePublication)

                val issueKey = if (cachedIssueKey != null) {
                    IssueKey(cachedIssueKey)
                } else {
                    // If the Issue metadata is not downloaded yet, we try to download it
                    downloadIssuePublication(issuePublication)
                }

                withContext(Dispatchers.Main) {
                    val continueReadDisplayable = if (displayableKey != null) {
                        issueViewerViewModel.setDisplayable(
                            issueKey,
                            displayableKey,
                            loadIssue = true
                        )
                    } else {
                        issueViewerViewModel.setDisplayable(issueKey, loadIssue = true)
                    }
                    val askEachTime = generalDataStore.settingsContinueReadAskEachTime.get()
                    if (continueReadDisplayable != null && askEachTime) {
                        if (childFragmentManager.findFragmentByTag(
                                ContinueReadBottomSheetFragment.TAG
                            ) == null
                        ) {
                            ContinueReadBottomSheetFragment.newInstance(continueReadDisplayable)
                                .show(
                                    childFragmentManager, ContinueReadBottomSheetFragment.TAG
                                )
                        }
                    }
                }
            }
        }
    }

    private suspend fun downloadIssuePublication(issuePublication: IssuePublication): IssueKey {
        val wasElapsed = authHelper.isElapsed()

        val issue = try {
            contentService.downloadIssueMetadata(issuePublication, maxRetries = 3)

        } catch (e: CacheOperationFailedException) {
            // In case of an error, we might show an error then retry infinitely

            // If an elapsed status is just found during the download of this issue,
            // then we won't show the issue loading error here, but let the code responsible to show
            // the [SubscriptionElapsedBottomSheetFragment] take over.
            val elapsedOnDownload = !wasElapsed && authHelper.isElapsed()
            if (!elapsedOnDownload) {
                issueViewerViewModel.issueLoadingFailedErrorFlow.emit(true)
            }
            contentService.downloadIssueMetadata(
                issuePublication,
                METADATA_DOWNLOAD_RETRY_INDEFINITELY
            )
        }


        return issue.issueKey
    }

    private suspend fun ContentService.downloadIssueMetadata(
        issuePublication: IssuePublication,
        maxRetries: Int
    ): Issue =
        downloadMetadata(
            issuePublication,
            maxRetries = maxRetries
        ) as Issue

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // show an error if downloading the metadata, the issue, or another file fails
        issueViewerViewModel.issueLoadingFailedErrorFlow
            .flowWithLifecycle(lifecycle)
            .filter { it }
            .onEach {
                activity?.showIssueDownloadFailedDialog(issuePublication)
            }.launchIn(lifecycleScope)

        issueViewerViewModel.showSubscriptionElapsedFlow
            .flowWithLifecycle(lifecycle)
            .distinctUntilChanged()
            .filter { it }
            .onEach {
                SubscriptionElapsedBottomSheetFragment.showSingleInstance(childFragmentManager)
            }.launchIn(lifecycleScope)

        // Check whether maybe show dialog to always continue read or always show title section
        val showFlow: Flow<Boolean> = combine(
            generalDataStore.settingsContinueReadAskEachTime.asFlow(),
            generalDataStore.settingsContinueReadDialogShown.asFlow(),
        ) { askEachTime, dialogShown ->
            askEachTime && !dialogShown
        }
            .filter { it }
            .distinctUntilChanged()

        combine(
            showFlow,
            generalDataStore.continueReadClicked.asFlow(),
        ) { _, readClicked -> readClicked }
            .flowWithLifecycle(lifecycle)
            .filter { it == SHOW_CONTINUE_READ_THE_SAME_NOT_MORE_THAN }
            .onEach {
                showContinueReadSettingDialog()
                generalDataStore.settingsContinueReadDialogShown.set(true)
            }
            .launchIn(lifecycleScope)

        combine(
            showFlow,
            generalDataStore.continueReadDismissed.asFlow(),
        ) { _, continueReadDismissed -> continueReadDismissed }
            .flowWithLifecycle(lifecycle)
            .filter { it == SHOW_CONTINUE_READ_THE_SAME_NOT_MORE_THAN }
            .onEach {
                showAlwaysTitleSectionSettingDialog()
                generalDataStore.settingsContinueReadDialogShown.set(true)
            }
            .launchIn(lifecycleScope)
    }

    override fun onLogInSuccessful(articleName: String?) {
        // Launch the Activity restarting logic from the application scope to prevent it from being
        // accidentally canceled due the the activity being finished
        viewLifecycleOwner.lifecycleScope.launch {
            // Restart the activity if this is *not* a Week/Wochentaz abo
            if (!authHelper.isLoginWeek.get()) {
                articleName
                    ?.takeIf { authHelper.isValid() }
                    ?.replace("public.", "")
                    ?.let { articleNameRegular ->
                        MainActivity.start(
                            requireContext(),
                            issuePublication,
                            articleNameRegular,
                        )
                    }
            } else {
                toastHelper.showToast(R.string.toast_login_week, long = true)
            }
        }
    }
}