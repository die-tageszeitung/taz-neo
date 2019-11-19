package de.taz.app.android.base

import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import androidx.core.view.iterator
import androidx.fragment.app.Fragment
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.navigation.NavigationView
import de.taz.app.android.R

abstract class BaseMainFragment<out PRESENTER : BaseContract.Presenter> : BaseFragment<PRESENTER>(),
    BaseContract.View {

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        configBottomNavigation()
    }

    override fun onResume() {
        super.onResume()

        // configure NavigationView @ Gravity.End
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
     * icons to show if an ItemNavigationId is active (currently selected)
     * map of ItemNavigationId [@MenuRes] to Drawable [@Drawable]
     */
    open val activeIconMap: Map<Int, Int> = mapOf()

    /**
     * icons to show if an ItemNavigationId is inactive (not currently selected)
     * map of ItemNavigationId [@MenuRes] to Drawable [@Drawable]
     */
    open val inactiveIconMap: Map<Int, Int> = mapOf()

    /**
     * used to store if an Item should be permanently active
     * i.e. bookmarks should always be active if article is bookmarked
     * and ignore currently selected item
     */
    private val permanentlyActiveItemIds = mutableListOf<Int>()

    /**
     * override to react to an item being clicked
     */
    open fun onBottomNavigationItemClicked(menuItem: MenuItem) = Unit

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
                    deactivateAllItems(menu)
                    toggleMenuItem(menuItem)
                    false
                }
            }

            setOnNavigationItemReselectedListener { menuItem ->
                run {
                    deactivateAllItems(menu)
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

    fun setIconActive(itemId: Int) {
        val menu = view?.findViewById<BottomNavigationView>(R.id.navigation_bottom)?.menu
        menu?.findItem(itemId)?.let { menuItem ->
            setIconActive(menuItem)
        }
    }

    fun setIconActive(menuItem: MenuItem) {
        activeIconMap[menuItem.itemId]?.let { menuItem.setIcon(it) }
    }

    fun setIconInactive(itemId: Int) {
        val menu = view?.findViewById<BottomNavigationView>(R.id.navigation_bottom)?.menu
        menu?.findItem(itemId)?.let { menuItem ->
            setIconInactive(menuItem)
        }
    }

    fun setIconInactive(menuItem: MenuItem) {
        inactiveIconMap[menuItem.itemId]?.let { menuItem.setIcon(it) }
    }

    private fun toggleMenuItem(menuItem: MenuItem) {
        val oldCheckable = menuItem.isChecked && menuItem.isCheckable
        if (!oldCheckable) {
            setIconActive(menuItem)
            onBottomNavigationItemClicked(menuItem)
        } else {
            setIconInactive(menuItem)
            onBottomNavigationItemClicked(menuItem)
        }
        menuItem.isChecked = !oldCheckable
        menuItem.isCheckable = !oldCheckable
    }

    fun isPermanentlyActive(itemId: Int): Boolean {
        return itemId in permanentlyActiveItemIds
    }

    fun isPermanentlyActive(menuItem: MenuItem): Boolean {
        return isPermanentlyActive(menuItem.itemId)
    }

    fun setPermanentlyActive(itemId: Int) {
        permanentlyActiveItemIds.add(itemId)
    }

    fun setPermanentlyActive(menuItem: MenuItem) {
        setPermanentlyActive(menuItem.itemId)
    }

    fun unsetPermanentlyActive(itemId: Int) {
        permanentlyActiveItemIds.remove(itemId)
    }

    fun unsetPermanentlyActive(menuItem: MenuItem) {
        permanentlyActiveItemIds.remove(menuItem.itemId)
    }

    private fun deactivateAllItems(menu: Menu) {
        menu.iterator().forEach {
            if (it.itemId !in permanentlyActiveItemIds) {
                inactiveIconMap[it.itemId]?.let { icon ->
                    it.setIcon(icon)
                }
            }
        }
    }


}