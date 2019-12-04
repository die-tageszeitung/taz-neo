package de.taz.app.android.ui.bottomSheet.textSettings

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import de.taz.app.android.R
import de.taz.app.android.base.BaseFragment
import kotlinx.android.synthetic.main.fragment_bottom_sheet_text_size.*

class TextSettingsFragment : BaseFragment<TextSizePresenter>(), TextSettingsContract.View {

    override val presenter = TextSizePresenter()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_bottom_sheet_text_size, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        presenter.attach(this)
        presenter.onViewCreated(savedInstanceState)

        fragment_bottom_sheet_text_size_settings?.setOnClickListener {
            presenter.onSettingsSelected()
        }

        fragment_bottom_sheet_text_size_text_size_decrease?.setOnClickListener {
            presenter.decreaseTextSize()
        }

        fragment_bottom_sheet_text_size_text_size_increase?.setOnClickListener {
            presenter.increaseTextSize()
        }

        fragment_bottom_sheet_text_size_night_mode?.setOnClickListener {
            presenter.onNightModeChanged(
                !fragment_bottom_sheet_text_size_night_mode_switch.isChecked
            )
        }

        fragment_bottom_sheet_text_size_night_mode_switch?.setOnClickListener {
            presenter.onNightModeChanged(
                fragment_bottom_sheet_text_size_night_mode_switch.isChecked
            )
        }
    }

    override fun setNightMode(active: Boolean) {
        fragment_bottom_sheet_text_size_night_mode_switch?.isChecked = active
    }

    override fun setTextSizePercentage(percent: String) {
        fragment_bottom_sheet_text_size_text_size_percentage?.text = "$percent%"
    }

}