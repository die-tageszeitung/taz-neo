package de.taz.app.android.ui.issueViewer

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.os.bundleOf
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.asFlow
import androidx.lifecycle.asLiveData
import androidx.lifecycle.lifecycleScope
import de.taz.app.android.R
import de.taz.app.android.api.models.AuthStatus
import de.taz.app.android.api.models.Issue
import de.taz.app.android.api.models.IssueStatus
import de.taz.app.android.audioPlayer.AudioPlayerViewController
import de.taz.app.android.content.ContentService
import de.taz.app.android.content.cache.CacheOperationFailedException
import de.taz.app.android.monkey.getApplicationScope
import de.taz.app.android.persistence.repository.IssueKey
import de.taz.app.android.persistence.repository.IssuePublication
import de.taz.app.android.singletons.AuthHelper
import de.taz.app.android.singletons.ToastHelper
import de.taz.app.android.ui.BackFragment
import de.taz.app.android.ui.SuccessfulLoginAction
import de.taz.app.android.ui.TazViewerFragment
import de.taz.app.android.ui.login.fragments.SubscriptionElapsedBottomSheetFragment
import de.taz.app.android.ui.navigation.BottomNavigationItem
import de.taz.app.android.ui.navigation.setBottomNavigationBackActivity
import de.taz.app.android.util.showIssueDownloadFailedDialog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.launch
import kotlin.reflect.KClass

/**
 * Activity to show an issue with sections and articles in a Pager.
 *
 * This class takes care of getting the metadata from the backend and handing the information to the
 * [IssueViewerViewModel]
 *
 * Additionally this activity shows a dialog if downloading the metadata fails - this error will be
 * triggered by other fragments as well (e.g. by [de.taz.app.android.ui.webview.WebViewFragment]
 * We want to have this error handling in one position to ensure we do not show the dialog more
 * often then necessary (once)
 *
 * If a user logs in via an [de.taz.app.android.ui.login.fragments.ArticleLoginFragment] this
 * activity gets an activityResult and will restart with the new issue.
 *
 */
class IssueViewerActivity : AppCompatActivity(), SuccessfulLoginAction {
    companion object {
        private const val KEY_ISSUE_PUBLICATION = "KEY_ISSUE_PUBLICATION"
        private const val KEY_DISPLAYABLE = "KEY_DISPLAYABLE"

        fun newIntent(
            packageContext: Context,
            issuePublication: IssuePublication,
            displayableKey: String? = null
        ) = Intent(packageContext, IssueViewerActivity::class.java).apply {
            putExtra(KEY_ISSUE_PUBLICATION, issuePublication)
            displayableKey?.let {
                putExtra(KEY_DISPLAYABLE, it)
            }
        }
    }

    @Suppress("unused")
    private val audioPlayerViewController = AudioPlayerViewController(this)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (savedInstanceState == null) {
            supportFragmentManager.beginTransaction().add(
                android.R.id.content,
                IssueViewerWrapperFragment.newInstance(
                    requireNotNull(intent.getParcelableExtra(KEY_ISSUE_PUBLICATION)) { "IssueViewerActivity must be instantiated with KEY_ISSUE_PUBLICATION" },
                    intent.getStringExtra(KEY_DISPLAYABLE),
                )
            ).commit()
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (audioPlayerViewController.onBackPressed()) {
            return
        }

        val fragment =
            supportFragmentManager.fragments.firstOrNull { it is IssueViewerWrapperFragment } as BackFragment
        if (fragment.onBackPressed()) {
            return
        }

        setBottomNavigationBackActivity(null, BottomNavigationItem.Home)
        super.onBackPressed()
    }

    override fun onResume() {
        super.onResume()
        setBottomNavigationBackActivity(this, BottomNavigationItem.Home)
    }

    override fun onDestroy() {
        super.onDestroy()
        setBottomNavigationBackActivity(null, BottomNavigationItem.Home)
    }

    override fun onLogInSuccessful(articleName: String) {
        // Launch the Activity restarting logic from the application scope to prevent it from being
        // accidentally canceled due the the activity being finished
        getApplicationScope().launch {
            // Restart the activity if this is *not* a Week/Wochentaz abo
            val authHelper = AuthHelper.getInstance(applicationContext)
            if (!authHelper.isLoginWeek.get()) {
                finish()
                startActivity(intent.putExtra(KEY_DISPLAYABLE, articleName))
            } else {
                finish()
                val toastHelper = ToastHelper.getInstance(applicationContext)
                toastHelper.showToast(R.string.toast_login_week, long = true)
            }
        }
    }
}

/**
 * This fragment downloads the given [IssuePublication] and uses an [IssueViewerFragment] to then
 * show the displayable
 *
 * This fragment encapsulates what was in the activity before refactoring
 * TODO Hopefully we can merge this with the [IssueViewerFragment]
 */
class IssueViewerWrapperFragment : TazViewerFragment() {

    val issuePublication: IssuePublication
        get() = requireNotNull(arguments?.getParcelable(KEY_ISSUE_PUBLICATION)) {
            "IssueViewerWrapperFragment needs to be started with KEY_ISSUE_KEY in Intent extras of type IssueKey"
        }
    private val displayableKey: String?
        get() = arguments?.getString(KEY_DISPLAYABLE)

    private lateinit var contentService: ContentService

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
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (savedInstanceState == null) {
            lifecycleScope.launch(Dispatchers.Main) {

                val issuePublication = this@IssueViewerWrapperFragment.issuePublication
                val cachedIssueKey = contentService.getIssueKey(issuePublication)

                val issueKey = if (cachedIssueKey != null) {
                    IssueKey(cachedIssueKey)
                } else {
                    // If the Issue metadata is not downloaded yet, we try to download it
                    suspend fun downloadMetadata(maxRetries: Int = -1) =
                        contentService.downloadMetadata(
                            issuePublication,
                            maxRetries = maxRetries
                        ) as Issue

                    val issue = try {
                        downloadMetadata(maxRetries = 3)
                    } catch (e: CacheOperationFailedException) {
                        // show error then retry infinitely
                        issueViewerViewModel.issueLoadingFailedErrorFlow.emit(true)
                        downloadMetadata()
                    }

                    issue.issueKey
                }

                if (displayableKey != null) {
                    issueViewerViewModel.setDisplayable(
                        issueKey,
                        displayableKey,
                        loadIssue = true
                    )
                } else {
                    issueViewerViewModel.setDisplayable(issueKey, loadIssue = true)
                }
            }
            checkElapsedAndShowBottomSheet()
        }

        // show an error if downloading the metadata, the issue, or another file fails
        issueViewerViewModel.issueLoadingFailedErrorFlow
            .filter { isError -> isError }
            .asLiveData()
            .observe(this) {
                checkElapsedAndShowBottomSheet(
                    showDownloadFailedDialogOnNotElapsed = true
                )
            }
    }

    /**
     * Show bottom sheet if user's subscription is elapsed and the issue status is public.
     * @param showDownloadFailedDialogOnNotElapsed Set true will show generic [showIssueDownloadFailedDialog].
     */
    private fun checkElapsedAndShowBottomSheet(showDownloadFailedDialogOnNotElapsed: Boolean = false) {
        lifecycleScope.launch {
            val subscriptionElapsed =
                issueViewerViewModel.elapsedSubscription.first() == AuthStatus.elapsed
            val isElapsedFormAlreadySent = issueViewerViewModel.elapsedFormAlreadySent.first()
            val issueKey = issueViewerViewModel.issueKeyAndDisplayableKeyLiveData.asFlow()
                .mapNotNull { it }.first().issueKey
            if (issueKey.status == IssueStatus.public && subscriptionElapsed && !isElapsedFormAlreadySent) {
                SubscriptionElapsedBottomSheetFragment().show(
                    childFragmentManager,
                    SubscriptionElapsedBottomSheetFragment.TAG
                )
            } else if (showDownloadFailedDialogOnNotElapsed) {
                requireActivity().showIssueDownloadFailedDialog(issuePublication)
            }
        }
    }

}