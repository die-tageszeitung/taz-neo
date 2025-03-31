package de.taz.app.android.api.models

import android.annotation.SuppressLint
import android.content.Context
import android.os.Bundle
import android.util.Log
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.View
import androidx.core.os.bundleOf
import androidx.core.view.GestureDetectorCompat
import androidx.core.view.ViewCompat.animate
import androidx.lifecycle.lifecycleScope
import de.taz.app.android.LOADING_SCREEN_FADE_OUT_TIME
import de.taz.app.android.R
import de.taz.app.android.base.BaseMainFragment
import de.taz.app.android.dataStore.TazApiCssDataStore
import de.taz.app.android.databinding.FragmentTomAtTheEndBinding
import de.taz.app.android.ui.webview.pager.ArticlePagerFragment
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

class TomAtTheEndFragment : BaseMainFragment<FragmentTomAtTheEndBinding>() {

    private lateinit var tazApiCssStore: TazApiCssDataStore

    private lateinit var gestureDetector: GestureDetector
    private var handleTapToScroll = false

    companion object {
        private const val ARG_TOM_RES_ID = "tom_res_id"
        fun newInstance(tomResId: Int) = TomAtTheEndFragment().apply {
            arguments = bundleOf(
                ARG_TOM_RES_ID to tomResId,
            )
        }
    }

    override fun onAttach(context: Context) {
        super.onAttach(context)
        tazApiCssStore = TazApiCssDataStore.getInstance(context.applicationContext)
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        gestureDetector = GestureDetector(requireContext(), onGestureListener)

        val tomResId = arguments?.getInt(ARG_TOM_RES_ID) ?: 0
        if (tomResId != 0) {
            viewBinding.tomImage.apply {
                setImageResource(tomResId)
                setOnTouchListener { _, event ->
                    gestureDetector.onTouchEvent(event)
                }
            }

            val multiFlow = tazApiCssStore.multiColumnMode.asFlow()
            val tapFlow = tazApiCssStore.tapToScroll.asFlow()
            val handleTapToScrollFlow =
                combine(multiFlow, tapFlow) { isMultiColumnMode, isTapToScroll ->
                    isMultiColumnMode || isTapToScroll
                }
            lifecycleScope.launch {
                handleTapToScrollFlow.collect {
                    handleTapToScroll = it
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        hideLoadingScreen()
    }

    private val onGestureListener = object : GestureDetector.SimpleOnGestureListener() {
        override fun onDown(e: MotionEvent): Boolean {
            return true
        }

        override fun onSingleTapUp(event: MotionEvent): Boolean {
            if (handleTapToScroll) {
                val tapBarWidth =
                    resources.getDimension(R.dimen.tap_bar_width)
                val onRightBorder = viewBinding.tomImage.right - event.x < tapBarWidth
                val onLeftBorder = event.x < tapBarWidth

                if (onRightBorder) {
                    pageRight()
                    return true

                } else if (onLeftBorder) {
                    pageLeft()
                    return true
                }
            }
            return false
        }
    }

    private fun pageRight() {
        (parentFragment as? ArticlePagerFragment)?.pageRight()
    }

    private fun pageLeft() {
        (parentFragment as? ArticlePagerFragment)?.pageLeft()
    }

    fun hideLoadingScreen() {
        viewBinding.loadingScreen.apply {
            animate()
                .alpha(0f)
                .withEndAction {
                    visibility = View.GONE
                }
                .duration = LOADING_SCREEN_FADE_OUT_TIME
        }
    }
}