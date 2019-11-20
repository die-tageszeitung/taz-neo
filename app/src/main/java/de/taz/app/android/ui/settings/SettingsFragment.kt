package de.taz.app.android.ui.settings

import android.os.Bundle
import androidx.lifecycle.LifecycleOwner
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import de.taz.app.android.R
import de.taz.app.android.base.BaseContract
import de.taz.app.android.ui.login.LoginFragment
import de.taz.app.android.ui.main.MainContract


class SettingsFragment : PreferenceFragmentCompat(), BaseContract.View  {

    override fun getMainView(): MainContract.View? {
        return activity as? MainContract.View
    }

    override fun getLifecycleOwner(): LifecycleOwner {
        return this
    }

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.fragment_settings_alt, rootKey)

        val manageAccountPreference : Preference? = findPreference("manage_account")
        manageAccountPreference?.setOnPreferenceClickListener {
            this.getMainView()?.showMainFragment(LoginFragment())
            true
        }

    }
}