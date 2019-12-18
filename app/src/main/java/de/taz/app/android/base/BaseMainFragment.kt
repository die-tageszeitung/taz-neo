package de.taz.app.android.base

import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import androidx.annotation.DrawableRes
import androidx.core.view.iterator
import androidx.fragment.app.Fragment
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.navigation.NavigationView
import de.taz.app.android.R

abstract class BaseMainFragment<out PRESENTER : BaseContract.Presenter> : BaseFragment<PRESENTER>(),
    BaseContract.View {

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        configBottomNavigation()
        addBottomSheetCallbacks()
    }

    override fun onResume() {
        super.onResume()
        setEndNavigation()
    }

    override fun onPause() {
        super.onPause()
        removeEndNavigationView()
    }

    /**
     * endNavigationFragment - the fragment to be shown in the
     * [NavigationView] at [Gravitiy.End]
     * if null NavigationView will not be openable
     */
    open val endNavigationFragment: Fragment? = null

    /**
     * show [endNavigationFragment]
     */
    private fun setEndNavigation() {
        endNavigationFragment?.let { endNavigationFragment ->
            activity?.apply {
                supportFragmentManager.beginTransaction()
                    .replace(R.id.nav_view_end_fragment_placeholder, endNavigationFragment)
                    .commit()
                getMainView()?.unlockEndNavigationView()
            }
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

    /**
     * used to store if an Item should be permanently active
     * i.e. bookmarks should always be active if article is bookmarked
     * and ignore currently selected item
     */
    private val permanentlyActiveItemIds = mutableListOf<Int>()

    /**
     * override to react to an item being clicked
     */
    open fun onBottomNavigationItemClicked(menuItem: MenuItem, activated: Boolean) = Unit

    /**
     * setup BottomNavigationBar
     * hacks to make icons de- and selectable
     */
    private fun configBottomNavigation() {
        // only show bottomNavigation if visible items exist

        view?.findViewById<BottomNavigationView>(R.id.navigation_bottom)?.apply {

            itemIconTintList = null

            deactivateAllItems(menu)

            // hack to not auto select first item
            menu.getItem(0).isCheckable = false

            // hack to make items de- and selectable
            setOnNavigationItemSelectedListener { menuItem ->
                run {
                    deactivateAllItems(menu, except = menuItem)
                    toggleMenuItem(menuItem)
                    false
                }
            }

            setOnNavigationItemReselectedListener { menuItem ->
                run {
                    deactivateAllItems(menu, except = menuItem)
                    toggleMenuItem(menuItem)
                }
            }
        }
    }

    fun toggleMenuItem(itemId: Int) {
        val menu = view?.findViewById<BottomNavigationView>(R.id.navigation_bottom)?.menu
        menu?.findItem(itemId)?.let { id ->
            toggleMenuItem(id)
        }
    }

    fun activateItem(itemId: Int) {
        val menu = view?.findViewById<BottomNavigationView>(R.id.navigation_bottom)?.menu
        menu?.findItem(itemId)?.let { menuItem ->
            activateItem(menuItem)
        }
    }

    fun activateItem(menuItem: MenuItem) {
        menuItem.isChecked = true
        menuItem.isCheckable = true
    }

    fun deactivateItem(itemId: Int) {
        val menu = view?.findViewById<BottomNavigationView>(R.id.navigation_bottom)?.menu
        menu?.findItem(itemId)?.let { menuItem ->
            deactivateItem(menuItem)
        }
    }

    fun deactivateItem(menuItem: MenuItem) {
        menuItem.isChecked = false
        menuItem.isCheckable = false
    }

    fun deactivateAllItems(menu: Menu, except: MenuItem? = null) {
        menu.iterator().forEach {
            if (it.itemId !in permanentlyActiveItemIds && it != except) {
                deactivateItem(it)
            }
        }
    }

    fun setIcon(itemId: Int, @DrawableRes iconRes: Int) {
        val menu = view?.findViewById<BottomNavigationView>(R.id.navigation_bottom)?.menu
        menu?.findItem(itemId)?.setIcon(iconRes)
    }

    private fun toggleMenuItem(menuItem: MenuItem) {
        if (menuItem.isCheckable) {
            deactivateItem(menuItem)
            onBottomNavigationItemClicked(menuItem, activated = false)
        } else {
            activateItem(menuItem)
            onBottomNavigationItemClicked(menuItem, activated = true)
        }
    }

    /**
     * callbacks for bottomSheet
     * to use this a class needs to have a View with id [R.id.bottom_sheet_behaviour]
     * which needs to have
     * app:layout_behavior="com.google.android.material.bottomsheet.BottomSheetBehavior"
     */
    internal open val bottomSheetCallback: BottomSheetBehavior.BottomSheetCallback? = null

    private fun addBottomSheetCallbacks() {
        bottomSheetCallback?.let {
            view?.findViewById<View>(R.id.bottom_sheet_behaviour)?.let {
                val bottomSheetBehavior = BottomSheetBehavior.from(it)
                bottomSheetBehavior.setBottomSheetCallback(bottomSheetCallback)
            }
        }
    }

    /**
     * check whether the bottomSheet is visible or not
     * @return true if visible else false
     */
    override fun isBottomSheetVisible(): Boolean {
        view?.findViewById<View>(R.id.bottom_sheet_behaviour)?.let {
            val bottomSheetBehavior = BottomSheetBehavior.from(it)
            return bottomSheetBehavior.state != BottomSheetBehavior.STATE_COLLAPSED
        }
        return false
    }


    /**
     * show bottomSheet
     * @param fragment: The [Fragment] which will be shown in the BottomSheet
     */
    override fun showBottomSheet(fragment: Fragment) {
        view?.findViewById<View>(R.id.bottom_sheet_behaviour)?.let {
            val bottomSheetBehavior = BottomSheetBehavior.from(it)

            childFragmentManager.beginTransaction().replace(
                R.id.bottom_sheet_behaviour, fragment
            ).commitNow()

            bottomSheetBehavior.state = BottomSheetBehavior.STATE_EXPANDED
        }
    }

    /**
     * hide bottomSheet
     */
    override fun hideBottomSheet() {
        view?.findViewById<View>(R.id.bottom_sheet_behaviour)?.let {
            val bottomSheetBehavior = BottomSheetBehavior.from(it)
            bottomSheetBehavior.state = BottomSheetBehavior.STATE_COLLAPSED
        }
    }

}