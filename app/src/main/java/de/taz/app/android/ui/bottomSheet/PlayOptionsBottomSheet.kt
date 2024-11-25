package de.taz.app.android.ui.bottomSheet

import android.content.Context
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import androidx.core.view.updateLayoutParams
import androidx.fragment.app.DialogFragment
import androidx.lifecycle.lifecycleScope
import com.google.android.material.bottomsheet.BottomSheetBehavior
import de.taz.app.android.R
import de.taz.app.android.api.interfaces.AudioPlayerPlayable
import de.taz.app.android.audioPlayer.AudioPlayerViewModel
import de.taz.app.android.base.ViewBindingBottomSheetFragment
import de.taz.app.android.databinding.FragmentBottomSheetPlayOptionsBinding
import de.taz.app.android.monkey.setBehaviorStateOnLandscape
import kotlinx.coroutines.launch

class PlayOptionsBottomSheet<PLAYABLE: AudioPlayerPlayable>(private val menuItemView: View) :
    ViewBindingBottomSheetFragment<FragmentBottomSheetPlayOptionsBinding>() {

    private lateinit var audioPlayerViewModel: AudioPlayerViewModel<PLAYABLE>

    companion object {
        const val TAG = "playOptionsBottomSheet"
        fun <PLAYABLE : AudioPlayerPlayable> newInstance(
            menuItemView: View,
            audioPlayerViewModel: AudioPlayerViewModel<PLAYABLE>,
        ): DialogFragment =
            PlayOptionsBottomSheet<PLAYABLE>(menuItemView).apply {
                this.audioPlayerViewModel = audioPlayerViewModel
            }
    }

    override fun onAttach(context: Context) {
        super.onAttach(context)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Necessary to get transparent background:
        setStyle(STYLE_NORMAL, R.style.PlayOptionsBottomSheetDialogTheme)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        positionTheBottomSheetAbovePlayButton()

        viewBinding.touchOutside.setOnClickListener {
            dismiss()
        }

        viewBinding.playAudio.setOnClickListener {
            tryPlayAudio()
        }
        viewBinding.playAudioIcon.setOnClickListener {
            tryPlayAudio()
        }

        viewBinding.enqueueAudio.setOnClickListener {
            tryEnqueueAudio()
        }
        viewBinding.enqueueAudioIcon.setOnClickListener {
            tryEnqueueAudio()
        }
    }

    override fun onStart() {
        super.onStart()
        setBehaviorStateOnLandscape(BottomSheetBehavior.STATE_EXPANDED)
    }

    private fun tryPlayAudio() {
        lifecycleScope.launch {
            audioPlayerViewModel.handleOnAudioActionOnVisible(
                playImmediately = true, playNext = true
            )
            dismiss()
        }
    }

    private fun tryEnqueueAudio() {
        lifecycleScope.launch {
            audioPlayerViewModel.handleOnAudioActionOnVisible(
                playImmediately = false, playNext = false
            )
            dismiss()
        }
    }

    private fun positionTheBottomSheetAbovePlayButton() {
        val totalWidth = resources.displayMetrics.widthPixels.toFloat()

        val beginningOfBottomLayout = (menuItemView.parent as View).x
        val middlePositionOfMenuItemView =
            beginningOfBottomLayout + menuItemView.x + 0.5 * menuItemView.width

        val audioIconWidth =
            resources.getDimensionPixelSize(R.dimen.audioplayer_play_options_icon_with_padding_size)
        val audioIconMarginEnd =
            resources.getDimensionPixelSize(R.dimen.audioplayer_play_options_small_margin)

        val middlePositionOfPlayOPtionsIcons = 0.5 * audioIconWidth + audioIconMarginEnd

        val newMarginEnd =
            totalWidth - middlePositionOfMenuItemView - middlePositionOfPlayOPtionsIcons
        viewBinding.layout.updateLayoutParams<ViewGroup.MarginLayoutParams> {
            marginStart = marginStart
            marginEnd = newMarginEnd.toInt()
        }
    }
}