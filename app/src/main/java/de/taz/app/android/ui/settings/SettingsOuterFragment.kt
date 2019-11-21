package de.taz.app.android.ui.settings

import android.os.Bundle
import android.view.LayoutInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import de.taz.app.android.R
import de.taz.app.android.base.BaseMainFragment

class SettingsOuterFragment : BaseMainFragment<SettingsContract.Presenter>() {

    override val presenter = SettingsPresenter()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_settings_outer, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        activity?.
            supportFragmentManager?.
            beginTransaction()?.
            replace(R.id.fragment_settings_outer_fragment_placeholder, SettingsInnerFragment())?.
            commit()

        presenter.attach(this)

    }

    override fun onBottomNavigationItemClicked(menuItem: MenuItem) {
        presenter.onBottomNavigationItemClicked(menuItem)
    }
}
