package de.taz.app.android.ui.settings

import android.os.Bundle
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import de.taz.app.android.TazApplication
import de.taz.app.android.api.models.AuthStatus
import de.taz.app.android.audioPlayer.AudioPlayerViewController
import de.taz.app.android.base.ViewBindingActivity
import de.taz.app.android.databinding.ActivitySettingsBinding
import de.taz.app.android.singletons.AuthHelper
import de.taz.app.android.ui.login.fragments.SubscriptionElapsedBottomSheetFragment
import de.taz.app.android.ui.navigation.BottomNavigationItem
import de.taz.app.android.ui.navigation.bottomNavigationBack
import de.taz.app.android.ui.navigation.setupBottomNavigation
import kotlinx.coroutines.launch

class SettingsActivity : ViewBindingActivity<ActivitySettingsBinding>() {

    @Suppress("unused")
    private val audioPlayerViewController = AudioPlayerViewController(this)

    private lateinit var authHelper: AuthHelper

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        authHelper = AuthHelper.getInstance(applicationContext)

        lifecycleScope.launch {
            lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
                checkIfSubscriptionElapsed()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        setupBottomNavigation(
            viewBinding.navigationBottom,
            BottomNavigationItem.Settings
        )
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (supportFragmentManager.backStackEntryCount > 0) {
            supportFragmentManager.popBackStack()
        } else {
            bottomNavigationBack()
        }
    }

    private suspend fun checkIfSubscriptionElapsed() {
        val authStatus = authHelper.status.get()
        val isElapsedButWaiting = authHelper.elapsedButWaiting.get()
        val isElapsedFormAlreadySent = authHelper.elapsedFormAlreadySent.get()
        val alreadyShown = (application as TazApplication).elapsedPopupAlreadyShown
        if (authStatus == AuthStatus.elapsed && !isElapsedButWaiting && !alreadyShown && !isElapsedFormAlreadySent) {
            showSubscriptionElapsedBottomSheet()
        }
    }

    private fun showSubscriptionElapsedBottomSheet() {
        SubscriptionElapsedBottomSheetFragment().show(
            supportFragmentManager,
            "showSubscriptionElapsed"
        )
    }
}