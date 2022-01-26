package de.taz.app.android.base

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.viewbinding.ViewBinding
import java.lang.reflect.ParameterizedType

abstract class ViewBindingFragment<ViewBindingClass : ViewBinding> : Fragment() {

    private var _binding: ViewBindingClass? = null

    // This property is only valid between onCreateView and onDestroyView.
    protected val viewBinding get() = _binding!!

    protected val rootView: View get() = viewBinding.root
    protected val rootViewGroup get() = rootView as? ViewGroup

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        _binding = createBinding(inflater, container)
        return rootView
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    @Suppress("UNCHECKED_CAST")
    private fun createBinding(layoutInflater: LayoutInflater, container: ViewGroup?): ViewBindingClass {
        val viewBindingClass =
            try {
                (javaClass.genericSuperclass as ParameterizedType).actualTypeArguments[0]
            } catch (cce: ClassCastException) {
                ((javaClass.genericSuperclass as Class<ViewBindingClass>)
                    .genericSuperclass as ParameterizedType).actualTypeArguments[0]
            } as Class<ViewBindingClass>
        val method = viewBindingClass.getMethod(
            "inflate",
            LayoutInflater::class.java,
            ViewGroup::class.java,
            Boolean::class.java
        )
        return method.invoke(this, layoutInflater, container, false) as ViewBindingClass
    }

}

