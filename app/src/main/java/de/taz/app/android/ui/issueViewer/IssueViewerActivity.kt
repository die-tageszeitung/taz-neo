package de.taz.app.android.ui.issueViewer

import android.content.Intent
import android.os.Bundle
import androidx.activity.viewModels
import androidx.lifecycle.asLiveData
import de.taz.app.android.api.models.Issue
import de.taz.app.android.content.ContentService
import de.taz.app.android.persistence.repository.IssuePublication
import de.taz.app.android.singletons.AuthHelper
import de.taz.app.android.ui.TazViewerActivity
import de.taz.app.android.ui.login.ACTIVITY_LOGIN_REQUEST_CODE
import de.taz.app.android.ui.main.MAIN_EXTRA_ARTICLE
import de.taz.app.android.ui.main.MAIN_EXTRA_TARGET
import de.taz.app.android.ui.main.MAIN_EXTRA_TARGET_ARTICLE
import de.taz.app.android.ui.main.MAIN_EXTRA_TARGET_HOME
import de.taz.app.android.util.showIssueDownloadFailedDialog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.launch
import kotlin.reflect.KClass


class IssueViewerActivity : TazViewerActivity() {
    private var finishOnBackPressed: Boolean = false
    private val issueViewerViewModel: IssueViewerViewModel by viewModels()
    private lateinit var issuePublication: IssuePublication
    private lateinit var contentService: ContentService
    private lateinit var authHelper: AuthHelper

    override val fragmentClass: KClass<IssueViewerFragment> = IssueViewerFragment::class

    companion object {
        const val KEY_ISSUE_PUBLICATION = "KEY_ISSUE_PUBLICATION"
        const val KEY_DISPLAYABLE = "KEY_DISPLAYABLE"
        const val KEY_FINISH_ON_BACK_PRESSED = "KEY_FINISHED_ON_BACK_PRESSED"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        authHelper = AuthHelper.getInstance(applicationContext)
        contentService = ContentService.getInstance(applicationContext)

        issuePublication = try {
            intent.getParcelableExtra(KEY_ISSUE_PUBLICATION)!!
        } catch (e: NullPointerException) {
            throw IllegalStateException("IssueViewerActivity needs to be started with KEY_ISSUE_KEY in Intent extras of type IssueKey")
        }
        if (savedInstanceState == null) {
            val displayableKey = intent.getStringExtra(KEY_DISPLAYABLE)
            finishOnBackPressed = intent.getBooleanExtra(KEY_FINISH_ON_BACK_PRESSED, false)
            CoroutineScope(Dispatchers.Main).launch {
                val issue = contentService.downloadMetadata(
                    issuePublication,
                    minStatus = authHelper.getMinStatus()
                ) as Issue
                if (displayableKey != null) {
                    issueViewerViewModel.setDisplayable(issue.issueKey, displayableKey, loadIssue = true)
                } else {
                    issueViewerViewModel.setDisplayable(issue.issueKey, loadIssue = true)
                }
            }
        }
        issueViewerViewModel.issueLoadingFailedErrorFlow
            .filter { isError -> isError }
            .asLiveData()
            .observe(this) {
                showIssueDownloadFailedDialog(issuePublication)
            }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode == ACTIVITY_LOGIN_REQUEST_CODE) {
            data?.let {
                data.getStringExtra(MAIN_EXTRA_TARGET)?.let {
                    if (it == MAIN_EXTRA_TARGET_ARTICLE) {
                        data.getStringExtra(MAIN_EXTRA_ARTICLE)?.let { articleName ->
                            Intent(this, IssueViewerActivity::class.java).apply {
                                putExtra(KEY_ISSUE_PUBLICATION, issuePublication)
                                putExtra(KEY_DISPLAYABLE, articleName.replace("public.", ""))
                                startActivity(this)
                                finish()
                            }
                        }
                    }
                    if (it == MAIN_EXTRA_TARGET_HOME) {
                        finish()
                    }
                }
            }
        }
    }

    override fun onBackPressed() {
        if (finishOnBackPressed) {
            finish()
        } else {
            super.onBackPressed()
        }
    }
}