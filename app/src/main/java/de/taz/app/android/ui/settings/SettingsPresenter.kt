package de.taz.app.android.ui.settings

import android.os.Bundle
import de.taz.app.android.base.BasePresenter
import de.taz.app.android.ui.login.LoginFragment

class SettingsPresenter : BasePresenter<SettingsFragment, SettingsDataController>(SettingsDataController::class.java) {
    override fun onViewCreated(savedInstanceState: Bundle?) {

    }

 //   fun showLoginFragment() {
 //       getView()?.getMainView()?.showMainFragment(LoginFragment())
 //   }

}