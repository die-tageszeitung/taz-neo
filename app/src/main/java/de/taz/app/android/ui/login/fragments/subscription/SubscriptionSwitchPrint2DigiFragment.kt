package de.taz.app.android.ui.login.fragments.subscription

import android.content.Context
import android.os.Bundle
import android.view.View
import de.taz.app.android.api.ApiService
import de.taz.app.android.base.BaseMainFragment
import de.taz.app.android.databinding.FragmentSwitchFormBinding
import de.taz.app.android.util.Log

class SubscriptionSwitchPrint2DigiFragment: BaseMainFragment<FragmentSwitchFormBinding>() {
    private val log by Log

    private lateinit var apiService: ApiService

    override fun onAttach(context: Context) {
        super.onAttach(context)
        apiService = ApiService.getInstance(requireContext().applicationContext)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        view.apply {
            viewBinding.fragmentSwitchSendButton.setOnClickListener {
                log.debug("BUTTON CLICKED: I want to switch")
            }
        }
    }
}