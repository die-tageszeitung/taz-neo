package de.taz.app.android.ui

import android.content.Context
import android.util.AttributeSet
import com.google.android.material.bottomnavigation.BottomNavigationView

class NoPaddingBottomNavigation @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : BottomNavigationView(context, attrs) {

    init {
        setOnApplyWindowInsetsListener(null)
        setPadding(0, 0, 0, 0)
    }
}
