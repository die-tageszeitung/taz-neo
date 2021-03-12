package de.taz.app.android.base

import android.app.Activity
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.inputmethod.InputMethodManager
import androidx.annotation.DrawableRes
import androidx.annotation.LayoutRes
import androidx.annotation.MenuRes
import androidx.core.view.iterator
import androidx.fragment.app.Fragment
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import de.taz.app.android.R
import de.taz.app.android.ui.bottomSheet.AddBottomSheetDialog
import java.lang.IndexOutOfBoundsException

abstract class BaseMainFragment(
    @LayoutRes layoutResourceId: Int
) : Fragment(layoutResourceId) {

    @MenuRes
    open val bottomNavigationMenuRes: Int? = null

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        configBottomNavigation()
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
    open fun onBottomNavigationItemClicked(menuItem: MenuItem) = Unit

    /**
     * setup BottomNavigationBar
     * hacks to make icons de- and selectable
     */
    private fun configBottomNavigation() {
        // only show bottomNavigation if visible items exist

        view?.findViewById<BottomNavigationView>(R.id.navigation_bottom)?.apply {

            bottomNavigationMenuRes?.let {
                menu.clear()
                inflateMenu(it)
            }

            itemIconTintList = null

            deactivateAllItems(menu)

            // hack to not auto select first item
            try {
                menu.getItem(0).isCheckable = false
            } catch (ioobe: IndexOutOfBoundsException) {
                // do nothing no items exist
            }

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
        val menuView = view?.findViewById<BottomNavigationView>(R.id.navigation_bottom)
        val menu = menuView?.menu
        // prevent call while layouting
        menuView?.post {
            menu?.findItem(itemId)?.setIcon(iconRes)
        }
    }

    private fun toggleMenuItem(menuItem: MenuItem) {
        if (menuItem.isCheckable) {
            deactivateItem(menuItem)
        } else {
            onBottomNavigationItemClicked(menuItem)
        }
    }

    /**
     * show bottomSheet
     * @param fragment: The [Fragment] which will be shown in the BottomSheet
     */
    fun showBottomSheet(fragment: Fragment) {
        val addBottomSheet =
            if (fragment is BottomSheetDialogFragment) {
                fragment
            } else {
                AddBottomSheetDialog.newInstance(fragment)
            }
        addBottomSheet.show(childFragmentManager, null)
    }

    protected fun hideKeyBoard() {
        activity?.apply {
            (getSystemService(Activity.INPUT_METHOD_SERVICE) as? InputMethodManager)?.apply {
                val view = activity?.currentFocus ?: View(activity)
                hideSoftInputFromWindow(view.windowToken, 0)
            }
        }
    }

    override fun onDetach() {
        hideKeyBoard()
        super.onDetach()
    }
}