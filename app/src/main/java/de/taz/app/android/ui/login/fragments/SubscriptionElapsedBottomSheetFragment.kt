package de.taz.app.android.ui.login.fragments

import android.annotation.SuppressLint
import android.content.Context
import android.os.Bundle
import android.view.MotionEvent
import android.view.View
import androidx.fragment.app.FragmentManager
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.google.android.material.bottomsheet.BottomSheetBehavior
import de.taz.app.android.R
import de.taz.app.android.api.models.AuthStatus
import de.taz.app.android.base.ViewBindingBottomSheetFragment
import de.taz.app.android.databinding.FragmentSubscriptionElapsedBottomSheetBinding
import de.taz.app.android.monkey.setBehaviorStateOnLandscape
import de.taz.app.android.singletons.AuthHelper
import de.taz.app.android.singletons.ToastHelper
import de.taz.app.android.tracking.Tracker
import de.taz.app.android.ui.login.fragments.SubscriptionElapsedBottomSheetViewModel.UIState
import de.taz.app.android.util.hideSoftInputKeyboard
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class SubscriptionElapsedBottomSheetFragment :
    ViewBindingBottomSheetFragment<FragmentSubscriptionElapsedBottomSheetBinding>() {

    companion object {
        const val TAG = "showSubscriptionElapsed"

        // Global flag used to ensure that the elapsed dialog is only shown once across all Fragments/Activities for each App instance
        var elapsedDialogAlreadyShown = MutableStateFlow(false)

        /**
         * Get a Flow that is returning true if the [SubscriptionElapsedBottomSheetFragment] should be shown.
         */
        fun AuthHelper.getShouldShowSubscriptionElapsedDialogFlow(): Flow<Boolean> = combine(
                status.asFlow(),
                elapsedFormAlreadySent.asFlow(),
                elapsedButWaiting.asFlow(),
                elapsedDialogAlreadyShown,
            ) { authStatus, isElapsedFormAlreadySent, isElapsedButWaiting, elapsedDialogAlreadyShown ->
                val isElapsed = authStatus == AuthStatus.elapsed
                !elapsedDialogAlreadyShown && isElapsed && !isElapsedFormAlreadySent&& !isElapsedButWaiting
            }

        /**
         * Returns true if the [SubscriptionElapsedBottomSheetFragment] should be shown.
         */
        suspend fun AuthHelper.shouldShowSubscriptionElapsedDialog(): Boolean =
            getShouldShowSubscriptionElapsedDialogFlow().first()

        /**
         * Show the [SubscriptionElapsedBottomSheetFragment] with its default [TAG].
         * Does nothing if a fragment with [TAG] is already present in the fragmentManger.
         */
        fun showSingleInstance(fragmentManager: FragmentManager) {
            if (fragmentManager.findFragmentByTag(TAG) == null
                // Prevent a crash that occurs if the show() method is called after onSaveInstanceState.
                && !fragmentManager.isStateSaved
            ) {
                elapsedDialogAlreadyShown.value = true
                SubscriptionElapsedBottomSheetFragment().show(
                    fragmentManager,
                    TAG
                )
            }
        }
    }

    private val viewModel by viewModels<SubscriptionElapsedBottomSheetViewModel>()
    private lateinit var toastHelper: ToastHelper
    private lateinit var tracker: Tracker

    override fun onStart() {
        super.onStart()
        setBehaviorStateOnLandscape(BottomSheetBehavior.STATE_EXPANDED)
    }

    override fun onAttach(context: Context) {
        super.onAttach(context)
        toastHelper = ToastHelper.getInstance(context.applicationContext)
        tracker = Tracker.getInstance(context.applicationContext)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        lifecycleScope.launch {
            viewBinding.title.text = viewModel.elapsedTitleStringFlow.first()
            viewBinding.description.text = viewModel.elapsedDescriptionStringFlow.first()
        }

        viewBinding.sendButton.setOnClickListener {
            viewModel.sendMessage(
                viewBinding.messageToSubscriptionService.editText?.text.toString(),
                viewBinding.letTheSubscriptionServiceContactYouCheckbox.isChecked
            )
        }

        viewBinding.buttonClose.setOnClickListener { dismiss() }

        viewBinding.roundedCornerWrapper.setOnTouchListener(object :
            View.OnTouchListener {
            @SuppressLint("ClickableViewAccessibility")
            override fun onTouch(view: View, event: MotionEvent): Boolean {
                hideSoftInputKeyboard()
                return false
            }
        })

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiStateFlow.collectLatest {
                    when (it) {
                        UIState.UnexpectedFailure -> {
                            toastHelper.showToast(R.string.something_went_wrong_try_later)
                            dismiss()
                        }

                        UIState.Sent -> {
                            toastHelper.showToast(
                                R.string.subscription_inquiry_send_success_toast,
                                long = true
                            )
                            dismiss()
                        }

                        UIState.FormInvalidMessageLength -> {
                            viewBinding.messageToSubscriptionService.error =
                                getString(R.string.popup_login_elapsed_message_to_short)
                        }

                        is UIState.SubmissionError -> {
                            val toastMessage = getString(
                                R.string.subscription_inquiry_submission_error,
                                it.message
                            )
                            toastHelper.showToast(toastMessage, long = true)
                            viewModel.errorWasHandled()
                        }

                        UIState.Init -> Unit // do nothing
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        tracker.trackSubscriptionElapsedDialog()
    }
}