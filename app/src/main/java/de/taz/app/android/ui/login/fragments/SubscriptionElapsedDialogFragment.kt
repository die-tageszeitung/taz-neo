package de.taz.app.android.ui.login.fragments

import android.os.Bundle
import android.view.View
import androidx.fragment.app.viewModels
import com.google.android.material.bottomsheet.BottomSheetDialog
import de.taz.app.android.R
import de.taz.app.android.base.ViewBindingBottomSheetFragment
import de.taz.app.android.databinding.FragmentSubscriptionElapsedDialogBinding


class SubscriptionElapsedDialogFragment : ViewBindingBottomSheetFragment<FragmentSubscriptionElapsedDialogBinding>() {

    private val viewModel by viewModels<SubscriptionElapsedDialogFragmentViewModel>()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        viewModel.elapsedString.observe(this) {
            viewBinding.title.text =
                getString(R.string.popup_login_elapsed_header, it)
        }

        viewBinding.sendButton.setOnClickListener {
            viewModel.sendMessage(
                viewBinding.messageToSubscriptionService.text.toString(),
                viewBinding.letTheSubscriptionServiceContactYouCheckbox.isChecked
            )
        }
    }

    override fun onResume() {
        super.onResume()
        (dialog as BottomSheetDialog).behavior.apply {
            isFitToContents = false
        }
    }
}