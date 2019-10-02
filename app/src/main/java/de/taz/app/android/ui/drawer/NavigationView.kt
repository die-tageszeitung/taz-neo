package de.taz.app.android.ui.drawer

import android.content.Context
import android.graphics.Rect
import android.util.AttributeSet
import android.view.View
import androidx.core.view.plusAssign
import com.google.android.material.navigation.NavigationView

class NavigationView @JvmOverloads constructor(
    context: Context, attributeSet: AttributeSet? = null, defStyleAttr: Int = 0
) : NavigationView(context, attributeSet, defStyleAttr) {

    override fun setVisibility(visibility: Int) {
        super.setVisibility(View.VISIBLE)
    }

}