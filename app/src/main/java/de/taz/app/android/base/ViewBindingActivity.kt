package de.taz.app.android.base

import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.viewbinding.ViewBinding
import de.taz.app.android.TazApplication
import de.taz.app.android.monkey.disableActivityAnimations
import java.lang.reflect.ParameterizedType

abstract class ViewBindingActivity<ViewBindingClass : ViewBinding> : AppCompatActivity() {

    lateinit var viewBinding: ViewBindingClass

    override fun onCreate(savedInstanceState: Bundle?) {
        // enable edge to edge for pre Android 15
        enableEdgeToEdge(
            SystemBarStyle.dark(Color.TRANSPARENT),
            SystemBarStyle.dark(Color.TRANSPARENT),
        )

        super.onCreate(savedInstanceState)
        disableActivityAnimations()

        viewBinding = createBinding(layoutInflater)
        setContentView(viewBinding.root)
        supportFragmentManager.setupForAccessibility()
    }

    @Suppress("UNCHECKED_CAST")
    private fun createBinding(layoutInflater: LayoutInflater): ViewBindingClass {
        val viewBindingClass =
            try {
                (javaClass.genericSuperclass as ParameterizedType).actualTypeArguments[0]
            } catch (cce: ClassCastException) {
                ((javaClass.genericSuperclass as Class<ViewBindingClass>)
                    .genericSuperclass as ParameterizedType).actualTypeArguments[0]
            } as Class<ViewBindingClass>
        val method = viewBindingClass.getMethod(
            "inflate",
            LayoutInflater::class.java
        )
        return method.invoke(this, layoutInflater) as ViewBindingClass
    }

    val applicationScope by lazy { (application as TazApplication).applicationScope }

}