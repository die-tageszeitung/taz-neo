package de.taz.app.android.base

import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
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
     * icons to show if an ItemNavigationId is active (currently selected)
     * map of ItemNavigationId [@MenuRes] to Drawable [@Drawable]
     */

    val activeIconMap = mapOf(
        R.id.bottom_navigation_action_bookmark to R.drawable.ic_bookmark_active,
        R.id.bottom_navigation_action_share to R.drawable.ic_share_active,
        R.id.bottom_navigation_action_size to R.drawable.ic_text_size_active,
        R.id.bottom_navigation_action_home to R.drawable.ic_home_active
    )

    /**
     * icons to show if an ItemNavigationId is inactive (not currently selected)
     * map of ItemNavigationId [@MenuRes] to Drawable [@Drawable]
     */
    val inactiveIconMap = mapOf(
        R.id.bottom_navigation_action_bookmark to R.drawable.ic_bookmark,
        R.id.bottom_navigation_action_share to R.drawable.ic_share,
        R.id.bottom_navigation_action_size to R.drawable.ic_text_size,
        R.id.bottom_navigation_action_home to R.drawable.ic_home
    )

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

    fun setIconActive(itemId: Int) {
        val menu = view?.findViewById<BottomNavigationView>(R.id.navigation_bottom)?.menu
        menu?.findItem(itemId)?.let { menuItem ->
            setIconActive(menuItem)
        }
    }

    fun setIconActive(menuItem: MenuItem) {
        menuItem.isChecked = true
        menuItem.isCheckable = true
        activeIconMap[menuItem.itemId]?.let { menuItem.setIcon(it) }
    }

    fun setIconInactive(itemId: Int) {
        val menu = view?.findViewById<BottomNavigationView>(R.id.navigation_bottom)?.menu
        menu?.findItem(itemId)?.let { menuItem ->
            setIconInactive(menuItem)
        }
    }

    fun setIconInactive(menuItem: MenuItem) {
        menuItem.isChecked = false
        menuItem.isCheckable = false
        inactiveIconMap[menuItem.itemId]?.let { menuItem.setIcon(it) }
    }

    private fun toggleMenuItem(menuItem: MenuItem) {
        if (menuItem.isCheckable) {
            setIconInactive(menuItem)
            onBottomNavigationItemClicked(menuItem, activated = false)
        } else {
            setIconActive(menuItem)
            onBottomNavigationItemClicked(menuItem, activated = true)
        }
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

    fun deactivateAllItems(menu: Menu, except: MenuItem? = null) {
        menu.iterator().forEach {
            if (it.itemId !in permanentlyActiveItemIds && it != except) {
                setIconInactive(it)
            }
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
     * show bottomSheet
     * @param fragment: The [Fragment] which will be shown in the BottomSheet
     */
    override fun showBottomSheet(fragment: Fragment) {
        view?.findViewById<View>(R.id.bottom_sheet_behaviour)?.let {
            val bottomSheetBehavior = BottomSheetBehavior.from(it)

            activity?.apply {
                supportFragmentManager.beginTransaction().replace(
                    R.id.bottom_sheet_behaviour, fragment
                ).commitNow()

                bottomSheetBehavior.state = BottomSheetBehavior.STATE_EXPANDED
            }
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