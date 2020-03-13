package de.taz.app.android.base

import android.view.Menu
import android.view.MenuItem
import androidx.annotation.DrawableRes
import androidx.core.view.iterator
import com.google.android.material.bottomnavigation.BottomNavigationView

interface BottomNavigationFragment {

    val bottomNavigationView: BottomNavigationView
    val menu
        get() = bottomNavigationView.menu

    fun setIcon(itemId: Int, @DrawableRes iconRes: Int) {
        menu.findItem(itemId)?.setIcon(iconRes)
    }

    private fun toggleMenuItem(menuItem: MenuItem) {
        if (menuItem.isCheckable) {
            deactivateItem(menuItem)
        } else {
            onBottomNavigationItemClicked(menuItem)
        }
    }

    /**
     * override to react to an item being clicked
     */
    fun onBottomNavigationItemClicked(menuItem: MenuItem) = Unit

    /**
     * setup BottomNavigationBar
     * hacks to make icons de- and selectable
     */
    fun configBottomNavigation() {
        // only show bottomNavigation if visible items exist

        bottomNavigationView.apply {

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
        menu.findItem(itemId)?.let { id ->
            toggleMenuItem(id)
        }
    }

    fun activateItem(itemId: Int) {
        menu.findItem(itemId)?.let { menuItem ->
            activateItem(menuItem)
        }
    }

    fun activateItem(menuItem: MenuItem) {
        menuItem.isChecked = true
        menuItem.isCheckable = true
    }

    fun deactivateItem(itemId: Int) {
        menu.findItem(itemId)?.let { menuItem ->
            deactivateItem(menuItem)
        }
    }

    fun deactivateItem(menuItem: MenuItem) {
        menuItem.isChecked = false
        menuItem.isCheckable = false
    }

    fun deactivateAllItems(menu: Menu, except: MenuItem? = null) {
        menu.iterator().forEach {
            if (it != except) {
                deactivateItem(it)
            }
        }
    }
}