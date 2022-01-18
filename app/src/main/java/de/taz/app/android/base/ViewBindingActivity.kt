package de.taz.app.android.base

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatActivity
import androidx.viewbinding.ViewBinding
import de.taz.app.android.util.Log
import java.lang.reflect.ParameterizedType

abstract class ViewBindingActivity<ViewBindingClass : ViewBinding> : AppCompatActivity() {

    private val log by Log

    lateinit var viewBinding: ViewBindingClass
    protected val rootView: View by lazy { viewBinding.root }
    protected val rootViewGroup by lazy { rootView as? ViewGroup }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        viewBinding = createBinding(layoutInflater)
        setContentView(viewBinding.root)
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

}