package de.taz.app.android.ui.login.fragments

import android.annotation.SuppressLint
import android.content.Context
import android.os.Bundle
import android.view.MotionEvent
import android.view.View
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import de.taz.app.android.R
import de.taz.app.android.base.ViewBindingBottomSheetFragment
import de.taz.app.android.databinding.FragmentSubscriptionElapsedBottomSheetBinding
import de.taz.app.android.monkey.doNotFlattenCorners
import de.taz.app.android.singletons.ToastHelper
import de.taz.app.android.tracking.Tracker
import de.taz.app.android.ui.login.fragments.SubscriptionElapsedBottomSheetViewModel.UIState
import de.taz.app.android.util.hideSoftInputKeyboard
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class SubscriptionElapsedBottomSheetFragment :
    ViewBindingBottomSheetFragment<FragmentSubscriptionElapsedBottomSheetBinding>() {

    private val viewModel by viewModels<SubscriptionElapsedBottomSheetViewModel>()
    private lateinit var toastHelper: ToastHelper
    private lateinit var tracker: Tracker

    override fun onStart() {
        super.onStart()

        val isLandscape =
            this.resources.displayMetrics.heightPixels < this.resources.displayMetrics.widthPixels
        val behavior = BottomSheetBehavior.from(requireView().parent as View)
        if (isLandscape) {
            behavior.state = BottomSheetBehavior.STATE_EXPANDED
        }
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

        (dialog as BottomSheetDialog).behavior.doNotFlattenCorners()

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiStateFlow.collectLatest {
                    when (it) {
                        UIState.UnexpectedFailure -> {
                            toastHelper.showToast(R.string.something_went_wrong_try_later)
                            dismiss()
                        }
                        UIState.Sent -> {
                            toastHelper.showToast(R.string.subscription_inquiry_send_success_toast, long=true)
                            dismiss()
                        }
                        UIState.FormInvalidMessageLength -> {
                            viewBinding.messageToSubscriptionService.error = getString(R.string.popup_login_elapsed_message_to_short)
                        }
                        is UIState.SubmissionError -> {
                            val toastMessage = getString(R.string.subscription_inquiry_submission_error, it.message)
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