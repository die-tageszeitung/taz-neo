package de.taz.app.android.base

import android.view.View
import android.view.ViewGroup
import androidx.annotation.IdRes
import androidx.annotation.LayoutRes
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.google.android.material.appbar.AppBarLayout
import com.google.android.material.navigation.NavigationView
import de.taz.app.android.R
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

abstract class BaseMainFragment<out PRESENTER : BaseContract.Presenter> : BaseFragment<PRESENTER>(),
    BaseContract.View {

    /**
     * headerLayoutId - the id of the header layout
     * if it is null no header will be shown
     * overwrite [configureHeader] if further configuration is necessary
     */
    @get:LayoutRes
    open val headerLayoutId: Int? = null

    /**
     * scrollViewId - the id of the view used to scroll
     * used to determine if view is currently at the top in [showHeader]
     */
    @get:IdRes
    open val scrollViewId: Int? = null

    open fun configureHeader(): Job? = null

    /**
     * endNavigationFragment - the fragment to be shown in the
     * [NavigationView] at [Gravitiy.End]
     * if null NavigationView will not be openable
     */
    open val endNavigationFragment: Fragment? = null

    override fun onResume() {
        super.onResume()

        // configure header
        setHeader()
        lifecycleScope.launch {
            configureHeader()?.join()
            showHeader()
        }

        // configure NavigationView @ Gravity.End
        setEndNavigation()
    }

    /**
     * inflates the [headerLayoutId] if given
     */
    private fun setHeader() {
        activity?.apply {
            headerLayoutId?.let { headerId ->
                findViewById<ViewGroup>(R.id.header_placeholder)?.apply {
                    addView(layoutInflater.inflate(headerId, this, false))
                }
            }
        }
    }

    /**
     * ensures the header is shown when creating new Fragment
     * does not show the header if fragment is paused and resumed and not at top
     */
    private fun showHeader() {
        activity?.apply {
            scrollViewId?.let {
                val scrollView = findViewById<View>(scrollViewId as Int)
                if (!scrollView.canScrollVertically(-1)) {
                    findViewById<AppBarLayout>(R.id.app_bar_layout)?.setExpanded(
                        true,
                        false
                    )
                }
            }
        }
    }

    /**
     * show [endNavigationFragment]
     */
    private fun setEndNavigation() {
        endNavigationFragment?.let { endNavigationFragment ->
            activity?.apply {
                findViewById<NavigationView>(R.id.nav_view_end)?.apply {
                    supportFragmentManager.beginTransaction()
                        .replace(R.id.nav_view_end_fragment_placeholder, endNavigationFragment)
                        .commit()
                }
                getMainView()?.unlockEndNavigationView()
            }
        }
    }


    override fun onPause() {
        super.onPause()
        removeHeader()
        removeEndNavigationView()
    }

    /**
     * remove header to be sure no header is shown if another fragment does not use it
     */
    private fun removeHeader() {
        activity?.apply {
            findViewById<ViewGroup>(R.id.header_placeholder)?.removeAllViews()
        }
    }

    /**
     * ensure endNavigationView can not be opened if another fragment doesn't have it
     */
    private fun removeEndNavigationView() {
        endNavigationFragment?.let { endNavigationFragment ->
            activity?.apply {
                findViewById<NavigationView>(R.id.nav_view_end)?.apply {
                    supportFragmentManager.beginTransaction()
                        .remove(endNavigationFragment)
                        .commit()
                }
            }
            getMainView()?.lockEndNavigationView()
        }
    }
}