package de.taz.app.android.ui.issueViewer

import android.content.Intent
import android.os.Bundle
import androidx.activity.viewModels
import androidx.lifecycle.asLiveData
import de.taz.app.android.R
import de.taz.app.android.api.models.IssueStatus
import de.taz.app.android.persistence.repository.IssueKey
import de.taz.app.android.singletons.DateHelper
import de.taz.app.android.ui.TazViewerActivity
import de.taz.app.android.ui.login.ACTIVITY_LOGIN_REQUEST_CODE
import de.taz.app.android.ui.main.MAIN_EXTRA_ARTICLE
import de.taz.app.android.ui.main.MAIN_EXTRA_TARGET
import de.taz.app.android.ui.main.MAIN_EXTRA_TARGET_ARTICLE
import de.taz.app.android.ui.main.MAIN_EXTRA_TARGET_HOME
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.launch
import kotlin.reflect.KClass


class IssueViewerActivity : TazViewerActivity() {
    private var finishOnBackPressed: Boolean = false
    private val issueViewerViewModel: IssueViewerViewModel by viewModels()
    private lateinit var issueKey: IssueKey


    override val fragmentClass: KClass<IssueViewerFragment> = IssueViewerFragment::class

    companion object {
        const val KEY_ISSUE_KEY = "KEY_ISSUE_KEY"
        const val KEY_DISPLAYABLE = "KEY_DISPLAYABLE"
        const val KEY_FINISH_ON_BACK_PRESSED = "KEY_FINISHED_ON_BACK_PRESSED"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        issueKey = try {
            intent.getParcelableExtra(KEY_ISSUE_KEY)!!
        } catch (e: NullPointerException) {
            throw IllegalStateException("IssueViewerActivity needs to be started with KEY_ISSUE_KEY in Intent extras of type IssueKey")
        }
        if (savedInstanceState == null) {
            val displayableKey = intent.getStringExtra(KEY_DISPLAYABLE)
            finishOnBackPressed = intent.getBooleanExtra(KEY_FINISH_ON_BACK_PRESSED, false)
            CoroutineScope(Dispatchers.Main).launch {
                if (displayableKey != null) {
                    issueViewerViewModel.setDisplayable(issueKey, displayableKey, loadIssue = true)
                } else {
                    issueViewerViewModel.setDisplayable(issueKey, loadIssue = true)
                }
            }
        }
        issueViewerViewModel.issueLoadingFailedErrorFlow
            .filter { isError -> isError }
            .asLiveData()
            .observe(this) {
                showIssueDownloadFailedDialog(issueKey)
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
                                putExtra(KEY_ISSUE_KEY, issueKey.copy(status = IssueStatus.regular))
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