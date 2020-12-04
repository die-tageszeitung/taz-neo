package de.taz.app.android.ui.main

import android.os.Bundle
import androidx.fragment.app.FragmentManager
import androidx.viewpager2.widget.ViewPager2
import de.taz.app.android.R
import de.taz.app.android.annotation.Mockable
import de.taz.app.android.api.interfaces.IssueOperations
import de.taz.app.android.api.models.AuthStatus
import de.taz.app.android.base.NightModeActivity
import de.taz.app.android.data.DataService
import de.taz.app.android.persistence.repository.ImageRepository
import de.taz.app.android.persistence.repository.IssueRepository
import de.taz.app.android.persistence.repository.SectionRepository
import de.taz.app.android.singletons.AuthHelper
import de.taz.app.android.singletons.FileHelper
import de.taz.app.android.singletons.ToastHelper
import de.taz.app.android.ui.home.HomeFragment
import de.taz.app.android.ui.home.page.coverflow.CoverflowFragment
import de.taz.app.android.ui.login.fragments.SubscriptionElapsedDialogFragment
import de.taz.app.android.util.Log


const val MAIN_EXTRA_TARGET = "MAIN_EXTRA_TARGET"
const val MAIN_EXTRA_TARGET_HOME = "MAIN_EXTRA_TARGET_HOME"
const val MAIN_EXTRA_TARGET_ARTICLE = "MAIN_EXTRA_TARGET_ARTICLE"
const val MAIN_EXTRA_ARTICLE = "MAIN_EXTRA_ARTICLE"

@Mockable
class MainActivity : NightModeActivity(R.layout.activity_main) {

    private var fileHelper: FileHelper? = null
    private var imageRepository: ImageRepository? = null
    private var sectionRepository: SectionRepository? = null
    private var toastHelper: ToastHelper? = null
    private lateinit var dataService: DataService
    private lateinit var issueRepository: IssueRepository
    private val log by Log

    companion object {
        const val KEY_RESULT_SKIP_TO_ISSUE_KEY = 13
        const val KEY_RESULT_SKIP_TO_NEWEST = 14
        const val KEY_ISSUE_KEY = "KEY_ISSUE_KEY"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        issueRepository = IssueRepository.getInstance(applicationContext)
        dataService = DataService.getInstance(applicationContext)
        fileHelper = FileHelper.getInstance(applicationContext)
        imageRepository = ImageRepository.getInstance(applicationContext)
        sectionRepository = SectionRepository.getInstance(applicationContext)
        toastHelper = ToastHelper.getInstance(applicationContext)

        checkIfSubscriptionElapsed()
    }

    private fun checkIfSubscriptionElapsed() {
        val authStatus = AuthHelper.getInstance(applicationContext).authStatus
        val isElapsedButWaiting = AuthHelper.getInstance(applicationContext).elapsedButWaiting
        if (authStatus == AuthStatus.elapsed && !isElapsedButWaiting) {
            showSubscriptionElapsedPopup()
        }
    }

    fun showHome(skipToFirst: Boolean = false, skipToIssue: IssueOperations? = null) {
        runOnUiThread {
            supportFragmentManager.popBackStackImmediate(
                null,
                FragmentManager.POP_BACK_STACK_INCLUSIVE
            )
            val homeFragment =
                supportFragmentManager.fragments.firstOrNull { it is HomeFragment } as? HomeFragment
            val coverFlowFragment =
                homeFragment?.childFragmentManager?.fragments?.firstOrNull { it is CoverflowFragment } as? CoverflowFragment
            this.findViewById<ViewPager2>(R.id.feed_archive_pager)?.apply {
                currentItem -= 1
            }
            if (skipToFirst) {
                coverFlowFragment?.skipToHome()
            } else {
                skipToIssue?.let { coverFlowFragment?.skipToKey(skipToIssue.issueKey) }
            }
        }
    }

    fun showSubscriptionElapsedPopup() {
        val popUpFragment = SubscriptionElapsedDialogFragment()
        popUpFragment.show(
            supportFragmentManager,
            "showSubscriptionElapsed"
        )
    }
}
