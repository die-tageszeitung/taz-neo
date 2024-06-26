package de.taz.app.android.ui.login.fragments

import android.os.Bundle
import android.view.View
import de.taz.app.android.databinding.FragmentLoginEmailAlreadyTakenBinding

class EmailAlreadyLinkedFragment : LoginBaseFragment<FragmentLoginEmailAlreadyTakenBinding>() {

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        viewBinding.fragmentLoginEmailAlreadyTakenInsertNew.setOnClickListener {
            viewModel.apply {
                username = ""
                password = ""
                viewModel.statusBeforeEmailAlreadyLinked?.let {
                    viewModel.status = it
                }
                viewModel.statusBeforeEmailAlreadyLinked = null
            }
        }
        viewBinding.fragmentLoginEmailAlreadyTakenContactEmail.setOnClickListener {
            writeEmail()
        }
    }

}