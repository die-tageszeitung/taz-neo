package de.taz.app.android.base

import android.os.Bundle
import android.view.LayoutInflater
import androidx.appcompat.app.AppCompatActivity
import androidx.viewbinding.ViewBinding
import de.taz.app.android.util.Log
import java.lang.reflect.ParameterizedType

abstract class ViewBindingActivity<ViewBindingClass : ViewBinding> :
    AppCompatActivity() {

    private val log by Log

    lateinit var binding: ViewBindingClass

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = createBinding(layoutInflater)
        setContentView(binding.root)
    }

    @Suppress("UNCHECKED_CAST")
    private fun createBinding(layoutInflater: LayoutInflater): ViewBindingClass {
        val viewBindingClass =
            (javaClass.genericSuperclass as ParameterizedType).actualTypeArguments[0] as Class<ViewBindingClass>
        val method = viewBindingClass.getMethod(
            "inflate",
            LayoutInflater::class.java
        )
        return method.invoke(layoutInflater) as ViewBindingClass
    }

}