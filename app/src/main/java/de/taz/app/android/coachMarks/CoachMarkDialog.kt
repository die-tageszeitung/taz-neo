package de.taz.app.android.coachMarks

import android.app.Dialog
import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import androidx.core.graphics.drawable.toDrawable
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.core.view.updateLayoutParams
import androidx.fragment.app.DialogFragment
import androidx.viewpager2.widget.ViewPager2
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import de.taz.app.android.R
import de.taz.app.android.databinding.FragmentCoachMarkDialogBinding
import de.taz.app.android.monkey.reduceDragSensitivity


class CoachMarkDialog : DialogFragment() {
    companion object {
        const val TAG = "CoachMarkFragment"

        fun create(coachMarks: List<BaseCoachMark>) = CoachMarkDialog().apply {
            this.coachMarks = coachMarks
        }
    }

    var coachMarks: List<BaseCoachMark> = emptyList()

    private lateinit var viewBinding: FragmentCoachMarkDialogBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        // dismiss on recreation - we don't want to take care of the state
        if (coachMarks.isEmpty()) this.dismiss()
        super.onCreate(savedInstanceState)
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        viewBinding = FragmentCoachMarkDialogBinding.inflate(layoutInflater)

        val dialog = MaterialAlertDialogBuilder(
            requireContext(),
            R.style.ThemeOverlay_App_MaterialAlertDialog_Fullscreen_Transparent
        )
            .setView(viewBinding.root)
            .create()


        val viewPager = viewBinding.coachMarkViewPager

        viewPager.apply {
            adapter = CoachMarkPagerAdapter(this@CoachMarkDialog)
            reduceDragSensitivity(6)
            offscreenPageLimit = 2
            registerOnPageChangeCallback(onPageChangeCallback)
        }

        viewBinding.buttonClose.setOnClickListener { this.dismiss() }
        viewBinding.buttonPrev.setOnClickListener { this.goPrev() }
        viewBinding.buttonNext.setOnClickListener { this.goNext() }

        dialog.window?.apply {
            setDimAmount(0.8f)
            setBackgroundDrawable(Color.TRANSPARENT.toDrawable())
        }

        applyExtraMarginForSystemBarInsets(viewBinding.buttonNext)
        applyExtraMarginForSystemBarInsets(viewBinding.buttonPrev)

        return dialog
    }

    private val onPageChangeCallback = object :
        ViewPager2.OnPageChangeCallback() {
        override fun onPageSelected(position: Int) {
            super.onPageSelected(position)
            viewBinding.buttonPrev.isVisible = position != 0
            viewBinding.buttonNext.isVisible = position != coachMarks.size - 1
            viewBinding.buttonCloseText.isVisible = position == coachMarks.size - 1
            if (coachMarks[position].moveCloseButtonToWhereNextIs) {
                moveCloseButtonDown()
            } else {
                // TODO check why this != 0 is necessary
                if (position != 0) setNormalCloseButtonPosition()
            }
        }
    }

    fun goNext() {
        viewBinding.coachMarkViewPager.currentItem = viewBinding.coachMarkViewPager.currentItem + 1
    }

    fun goPrev() {
        viewBinding.coachMarkViewPager.currentItem = viewBinding.coachMarkViewPager.currentItem - 1
    }

    private fun moveCloseButtonDown() {
        viewBinding.buttonClose.y = viewBinding.buttonPrev.y
    }

    private fun setNormalCloseButtonPosition() {
        viewBinding.buttonClose.y =
            resources.getDimensionPixelSize(R.dimen.coach_mark_button_close_margin_top).toFloat()
    }

    private fun applyExtraMarginForSystemBarInsets(view: View) {
        ViewCompat.setOnApplyWindowInsetsListener(view) { v, windowInsets ->
            val insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars())
            // Apply the insets as a margin to the view. This solution sets
            // only the bottom, left, and right dimensions, but you can apply whichever
            // insets are appropriate to your layout. You can also update the view padding
            // if that's more appropriate.
            val marginBottomFromDimens = resources.getDimensionPixelSize(R.dimen.fab_margin_bottom)
            v.updateLayoutParams<ViewGroup.MarginLayoutParams> {
                bottomMargin = insets.bottom + marginBottomFromDimens
            }

            // Return CONSUMED if you don't want the window insets to keep passing
            // down to descendant views.
            WindowInsetsCompat.CONSUMED
        }
    }
}