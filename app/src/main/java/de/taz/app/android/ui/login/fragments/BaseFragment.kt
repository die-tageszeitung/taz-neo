package de.taz.app.android.ui.login.fragments

import androidx.annotation.LayoutRes
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import de.taz.app.android.ui.login.LoginViewModel

abstract class BaseFragment(@LayoutRes layoutId: Int): Fragment(layoutId) {
    protected val lazyViewModel = activityViewModels<LoginViewModel>()
}