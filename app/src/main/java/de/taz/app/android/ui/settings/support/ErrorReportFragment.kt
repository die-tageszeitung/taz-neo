package de.taz.app.android.ui.settings.support

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import de.taz.app.android.R
import de.taz.app.android.base.BaseMainFragment
import de.taz.app.android.util.Log

class ErrorReportFragment : BaseMainFragment<ErrorReportContract.Presenter>(), ErrorReportContract.View {

    override val presenter = ErrorReportPresenter()
    private val log by Log

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_error_report, container, false)
    }
}
