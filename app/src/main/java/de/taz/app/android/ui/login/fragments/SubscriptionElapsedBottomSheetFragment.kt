package de.taz.app.android.ui.login.fragments

import android.os.Bundle
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
import kotlinx.coroutines.launch


class SubscriptionElapsedBottomSheetFragment :
    ViewBindingBottomSheetFragment<FragmentSubscriptionElapsedBottomSheetBinding>() {

    override fun getTheme(): Int = R.style.AppTheme_BottomSheetMenuTheme

    private val viewModel by viewModels<SubscriptionElapsedBottomSheetViewModel>()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        viewModel.elapsedString.observe(this) {
            viewBinding.title.text = it?.let { getString(R.string.popup_login_elapsed_header, it) }
                ?: getString(R.string.popup_login_elapsed_header_no_date)
        }

        viewBinding.sendButton.setOnClickListener {
            viewModel.sendMessage(
                viewBinding.messageToSubscriptionService.text.toString(),
                viewBinding.letTheSubscriptionServiceContactYouCheckbox.isChecked
            )
        }

        viewBinding.buttonClose.setOnClickListener { dismiss() }

        (dialog as BottomSheetDialog).behavior.doNotFlattenCorners()

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.RESUMED) {
                viewModel.hideLiveData.observe(this@SubscriptionElapsedBottomSheetFragment) {
                    if(it) {
                        ToastHelper.getInstance(requireContext().applicationContext).showToast(
                            R.string.something_went_wrong_try_later
                        )
                        dismiss()
                    }
                }
            }
        }
    }
}