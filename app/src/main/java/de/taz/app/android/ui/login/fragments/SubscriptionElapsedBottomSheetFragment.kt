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
import com.google.android.material.bottomsheet.BottomSheetDialog
import de.taz.app.android.R
import de.taz.app.android.base.ViewBindingBottomSheetFragment
import de.taz.app.android.databinding.FragmentSubscriptionElapsedBottomSheetBinding
import de.taz.app.android.monkey.doNotFlattenCorners
import de.taz.app.android.singletons.ToastHelper
import de.taz.app.android.ui.login.fragments.SubscriptionElapsedBottomSheetViewModel.UIState
import de.taz.app.android.util.hideSoftInputKeyboard
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class SubscriptionElapsedBottomSheetFragment :
    ViewBindingBottomSheetFragment<FragmentSubscriptionElapsedBottomSheetBinding>() {

    override fun getTheme(): Int = R.style.AppTheme_BottomSheetMenuTheme

    private val viewModel by viewModels<SubscriptionElapsedBottomSheetViewModel>()
    private lateinit var toastHelper: ToastHelper

    override fun onAttach(context: Context) {
        super.onAttach(context)
        toastHelper = ToastHelper.getInstance(requireContext().applicationContext)
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

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.RESUMED) {
                viewModel.uiStateFlow.collectLatest {
                    when (it) {
                        UIState.ERROR -> {
                            toastHelper.showToast(R.string.something_went_wrong_try_later)
                            dismiss()
                        }
                        UIState.SENT -> {
                            toastHelper.showToast(R.string.subscription_inquiry_send_success_toast, long=true)
                            dismiss()
                        }
                        else -> {
                            // do nothing
                        }
                    }
                }
            }
        }
    }
}