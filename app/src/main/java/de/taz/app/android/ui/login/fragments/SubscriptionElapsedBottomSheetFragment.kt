package de.taz.app.android.ui.login.fragments

import android.os.Bundle
import android.view.View
import androidx.fragment.app.viewModels
import com.google.android.material.bottomsheet.BottomSheetDialog
import de.taz.app.android.R
import de.taz.app.android.base.ViewBindingBottomSheetFragment
import de.taz.app.android.databinding.FragmentSubscriptionElapsedDialogBinding
import de.taz.app.android.monkey.doNotFlattenCorners


class SubscriptionElapsedBottomSheetFragment :
    ViewBindingBottomSheetFragment<FragmentSubscriptionElapsedDialogBinding>() {

    override fun getTheme(): Int = R.style.AppTheme_BottomSheetMenuTheme

    private val viewModel by viewModels<SubscriptionElapsedDialogFragmentViewModel>()

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
    }
}