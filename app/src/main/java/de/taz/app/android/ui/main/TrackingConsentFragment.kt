package de.taz.app.android.ui.main

import android.content.Context
import android.os.Bundle
import android.view.View
import androidx.annotation.StringRes
import androidx.core.os.bundleOf
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import de.taz.app.android.WEBVIEW_HTML_FILE_DATA_POLICY
import de.taz.app.android.base.ViewBindingFragment
import de.taz.app.android.dataStore.GeneralDataStore
import de.taz.app.android.databinding.FragmentTrackingConsentBinding
import de.taz.app.android.getTazApplication
import de.taz.app.android.ui.WebViewActivity
import kotlinx.coroutines.launch

interface TrackingConsentFragmentCallback {
    fun onTrackingConsentDone()
}

class TrackingConsentFragment : ViewBindingFragment<FragmentTrackingConsentBinding>() {

    private lateinit var generalDataStore: GeneralDataStore

    companion object {
        private const val ARG_PAGE = "page"
        private const val ARG_PAGE_TOTAL = "total"
        private const val ARG_HEADLINE_STRING = "headline"

        fun newInstance() = TrackingConsentFragment()
        fun newInstance(@StringRes headlineStringRes: Int, page: Int, pageTotal: Int) =
            TrackingConsentFragment().apply {
                arguments = bundleOf(
                    ARG_PAGE to page,
                    ARG_PAGE_TOTAL to pageTotal,
                    ARG_HEADLINE_STRING to headlineStringRes
                )
            }
    }

    override fun onAttach(context: Context) {
        super.onAttach(context)
        generalDataStore = GeneralDataStore.getInstance(context.applicationContext)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        viewBinding.apply {
            acceptTrackingButton.setOnClickListener {
                onAcceptTracking()
            }

            rejectTrackingButton.setOnClickListener {
                onRejectTracking()
            }

            privacyPolicyText.setOnClickListener {
                showDataPolicy()
            }

            pagerTitle.apply {
                val page = arguments?.getInt(ARG_PAGE) ?: 0
                val pageTotal = arguments?.getInt(ARG_PAGE_TOTAL) ?: 0
                val headlineStringRes = arguments?.getInt(ARG_HEADLINE_STRING) ?: 0
                when {
                    page > 0 && pageTotal > 0 && headlineStringRes != 0 -> {
                        isVisible = true
                        text = getString(headlineStringRes, page, pageTotal)
                    }
                    headlineStringRes != 0 -> {
                        isVisible = true
                        text = getString(headlineStringRes)
                    }
                    else -> {
                        isVisible = false
                    }
                }

            }
        }
    }

    override fun onResume() {
        super.onResume()
        lifecycleScope.launch {
            generalDataStore.hasBeenAskedForTrackingConsent.set(true)
        }
    }

    private fun showDataPolicy() {
        activity?.apply {
            val intent = WebViewActivity.newIntent(this, WEBVIEW_HTML_FILE_DATA_POLICY)
            startActivity(intent)
        }
    }

    private fun onAcceptTracking() {
        getTazApplication().applicationScope.launch {
            generalDataStore.consentToTracking.set(true)
        }
        done()
    }

    private fun onRejectTracking() {
        getTazApplication().applicationScope.launch {
            generalDataStore.consentToTracking.set(false)
        }
        done()
    }

    private fun done() {
        (parentFragment as? TrackingConsentFragmentCallback)?.onTrackingConsentDone()
    }
}