package de.taz.app.android.ui.webview

import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import androidx.core.view.iterator
import com.google.android.material.bottomnavigation.BottomNavigationView
import de.taz.app.android.R
import de.taz.app.android.base.BaseContract
import de.taz.app.android.base.BaseMainFragment


abstract class WebViewBaseFragment<PRESENTER: BaseContract.Presenter> : BaseMainFragment<PRESENTER>() {

    abstract override val presenter: PRESENTER

    abstract val visibleItemIds: List<Int>

    private val permanentlyActiveItemIds = mutableListOf<Int>()

    private val inactiveIconMap = mapOf(
        R.id.bottom_navigation_action_bookmark to R.drawable.ic_bookmark,
        R.id.bottom_navigation_action_help to R.drawable.ic_help,
        R.id.bottom_navigation_action_share to R.drawable.ic_share,
        R.id.bottom_navigation_action_size to R.drawable.ic_text_size
    )

    private val activeIconMap = mapOf(
        R.id.bottom_navigation_action_bookmark to R.drawable.ic_bookmark_active,
        R.id.bottom_navigation_action_help to R.drawable.ic_help_active,
        R.id.bottom_navigation_action_share to R.drawable.ic_share_active,
        R.id.bottom_navigation_action_size to R.drawable.ic_text_size_active
    )

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setBottomNavigation()
    }

    private fun setBottomNavigation() {
        activity?.findViewById<BottomNavigationView>(R.id.navigation_bottom)?.apply {

            itemIconTintList = null

            deactivateAllItems(menu)

            // show only wanted items
            menu.iterator().forEach { menuItem ->
                if (menuItem.itemId in visibleItemIds) {
                    if (!menuItem.isVisible) {
                        menuItem.isVisible = true
                    }
                } else {
                    if (menuItem.isVisible) {
                        menuItem.isVisible = false
                    }
                }
            }

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
        val menu = activity?.findViewById<BottomNavigationView>(R.id.navigation_bottom)?.menu
        menu?.findItem(itemId)?.let { id ->
            toggleMenuItem(id)
        }
    }

    fun setIconActive(itemId: Int) {
        val menu = activity?.findViewById<BottomNavigationView>(R.id.navigation_bottom)?.menu
        menu?.findItem(itemId)?.let { menuItem ->
            setIconActive(menuItem)
        }
    }

    fun setIconActive(menuItem: MenuItem) {
        activeIconMap[menuItem.itemId]?.let { menuItem.setIcon(it) }
    }

    fun setIconInactive(itemId: Int) {
        val menu = activity?.findViewById<BottomNavigationView>(R.id.navigation_bottom)?.menu
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

    abstract fun onBottomNavigationItemClicked(menuItem: MenuItem)

    fun isPermanentlyActive(itemId: Int) : Boolean {
        return itemId in permanentlyActiveItemIds
    }

    fun isPermanentlyActive(menuItem: MenuItem) : Boolean {
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
