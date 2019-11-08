package de.taz.app.android.base

import android.view.View
import android.view.ViewGroup
import androidx.annotation.IdRes
import androidx.annotation.LayoutRes
import androidx.lifecycle.lifecycleScope
import com.google.android.material.appbar.AppBarLayout
import de.taz.app.android.R
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

abstract class BaseHeaderFragment<out PRESENTER : BaseContract.Presenter> :
    BaseFragment<PRESENTER>() {

    @get:LayoutRes
    open val headerLayoutId: Int? = null

    @get:IdRes
    open val scrollViewId: Int? = null

    open fun configureHeader(): Job? = null


    override fun onResume() {
        super.onResume()
        setHeader()
        lifecycleScope.launch {
            configureHeader()?.join()
            showHeader()
        }
    }

    private fun setHeader() {
        activity?.apply {
            headerLayoutId?.let { headerId ->
                findViewById<ViewGroup>(R.id.header_placeholder)?.apply {
                    addView(layoutInflater.inflate(headerId, this, false))
                }
            }
        }
    }

    private fun showHeader() {
        scrollViewId ?.let {
            activity?.apply {
                val scrollView = findViewById<View>(scrollViewId as Int)
                if (!scrollView.canScrollVertically(-1))
                    findViewById<AppBarLayout>(R.id.app_bar_layout)?.setExpanded(
                        true,
                        false
                    )
            }
        }
    }

    override fun onPause() {
        super.onPause()
        removeHeader()
    }

    private fun removeHeader() {
        activity?.apply {
            findViewById<ViewGroup>(R.id.header_placeholder)?.removeAllViews()
        }
    }

}
