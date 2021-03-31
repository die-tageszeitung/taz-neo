package de.taz.app.android.ui.main

import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.widget.ImageButton
import androidx.activity.viewModels
import androidx.fragment.app.FragmentManager
import androidx.viewpager2.widget.ViewPager2
import de.taz.app.android.PREFERENCES_GENERAL
import de.taz.app.android.R
import de.taz.app.android.SETTINGS_HELP_TRY_PDF_SHOWN
import de.taz.app.android.annotation.Mockable
import de.taz.app.android.api.models.AuthStatus
import de.taz.app.android.base.NightModeActivity
import de.taz.app.android.data.DataService
import de.taz.app.android.monkey.observeDistinctIgnoreFirst
import de.taz.app.android.persistence.repository.ImageRepository
import de.taz.app.android.persistence.repository.IssueKey
import de.taz.app.android.persistence.repository.IssueRepository
import de.taz.app.android.persistence.repository.SectionRepository
import de.taz.app.android.singletons.*
import de.taz.app.android.ui.home.HomeFragment
import de.taz.app.android.ui.home.page.IssueFeedViewModel
import de.taz.app.android.ui.home.page.coverflow.CoverflowFragment
import de.taz.app.android.ui.login.ACTIVITY_LOGIN_REQUEST_CODE
import de.taz.app.android.ui.login.LoginActivity
import de.taz.app.android.ui.login.fragments.SubscriptionElapsedDialogFragment


const val MAIN_EXTRA_TARGET = "MAIN_EXTRA_TARGET"
const val MAIN_EXTRA_TARGET_HOME = "MAIN_EXTRA_TARGET_HOME"
const val MAIN_EXTRA_TARGET_ARTICLE = "MAIN_EXTRA_TARGET_ARTICLE"
const val MAIN_EXTRA_ARTICLE = "MAIN_EXTRA_ARTICLE"

@Mockable
class MainActivity : NightModeActivity(R.layout.activity_main) {

    private var fileHelper: StorageService? = null
    private var imageRepository: ImageRepository? = null
    private var sectionRepository: SectionRepository? = null
    private var toastHelper: ToastHelper? = null
    private lateinit var dataService: DataService
    private lateinit var issueRepository: IssueRepository
    private lateinit var authHelper: AuthHelper

    val issueFeedViewModel: IssueFeedViewModel by viewModels()

    companion object {
        const val KEY_ISSUE_KEY = "KEY_ISSUE_KEY"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        issueRepository = IssueRepository.getInstance(applicationContext)
        authHelper = AuthHelper.getInstance(applicationContext)
        dataService = DataService.getInstance(applicationContext)
        fileHelper = StorageService.getInstance(applicationContext)
        imageRepository = ImageRepository.getInstance(applicationContext)
        sectionRepository = SectionRepository.getInstance(applicationContext)
        toastHelper = ToastHelper.getInstance(applicationContext)

        checkIfSubscriptionElapsed()
        maybeShowTryPdfDialog()
    }

    override fun onResume() {
        super.onResume()
        if (issueFeedViewModel.pdfMode.value == true && !authHelper.isLoggedIn()) {
            AlertDialog.Builder(this)
                .setMessage(R.string.pdf_mode_better_to_be_logged_in_hint)
                .setPositiveButton(android.R.string.ok) { dialog, _ -> dialog.dismiss() }
                .setNegativeButton(R.string.login_button) { dialog, _ ->
                    startActivityForResult(
                        Intent(this, LoginActivity::class.java),
                        ACTIVITY_LOGIN_REQUEST_CODE
                    )
                    dialog.dismiss()
                }
                .show()
        }
        issueFeedViewModel.pdfMode.observeDistinctIgnoreFirst(this) {
            recreate()
        }
    }

    private fun maybeShowTryPdfDialog() {
        val preferences =
            applicationContext.getSharedPreferences(PREFERENCES_GENERAL, Context.MODE_PRIVATE)
        val timesPdfShown = preferences.getInt(SETTINGS_HELP_TRY_PDF_SHOWN, 0)
        if (timesPdfShown < 1) {
            val dialog = AlertDialog.Builder(this)
                .setView(R.layout.dialog_try_pdf)
                .setPositiveButton(android.R.string.ok) { dialog, _ ->
                    preferences.edit().apply {
                        putInt(SETTINGS_HELP_TRY_PDF_SHOWN, timesPdfShown + 1)
                        apply()
                    }
                    dialog.dismiss()
                }
                .show()
            // force this dialog to be white with black text (ignoring night mode and system theme)
            // - the animation is not compatible with other shades
            dialog.window?.setBackgroundDrawable(ColorDrawable(Color.WHITE))
            dialog.findViewById<ImageButton>(R.id.button_close).setOnClickListener {
                dialog.dismiss()
            }
        }
    }

    private fun checkIfSubscriptionElapsed() {
        val authStatus = AuthHelper.getInstance(applicationContext).authStatus
        val isElapsedButWaiting = AuthHelper.getInstance(applicationContext).elapsedButWaiting
        if (authStatus == AuthStatus.elapsed && !isElapsedButWaiting) {
            showSubscriptionElapsedPopup()
        }
    }

    fun showHome(skipToFirst: Boolean = false, issueKey: IssueKey? = null) {
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
                issueKey?.let { coverFlowFragment?.skipToKey(it) }
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
