package de.taz.app.android.base

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.viewbinding.ViewBinding
import com.bumptech.glide.Glide
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import de.taz.app.android.TazApplication
import java.io.File
import java.lang.reflect.ParameterizedType

/**
 * The basic ViewBindingFragment which uses a ViewBinding to populate
 * Make sure that the ViewBinding is always the last generic!
 * Otherwise [createBinding] will fail
 */
abstract class ViewBindingBottomSheetFragment<VIEW_BINDING : ViewBinding> :
    BottomSheetDialogFragment() {

    private var _binding: VIEW_BINDING? = null

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
    private fun createBinding(layoutInflater: LayoutInflater, container: ViewGroup?): VIEW_BINDING {
        val viewBindingClass =
            try {
                (javaClass.genericSuperclass as ParameterizedType).actualTypeArguments.last()
            } catch (cce: ClassCastException) {
                ((javaClass.genericSuperclass as Class<VIEW_BINDING>)
                    .genericSuperclass as ParameterizedType).actualTypeArguments.last()
            } as Class<VIEW_BINDING>
        val method = viewBindingClass.getMethod(
            "inflate",
            LayoutInflater::class.java,
            ViewGroup::class.java,
            Boolean::class.java
        )
        return method.invoke(this, layoutInflater, container, false) as VIEW_BINDING
    }

    protected val applicationScope by lazy {
        (requireActivity().application as TazApplication).applicationScope
    }

    fun ImageView.loadImageFileWithGlide(filePath: String) {
        if (File(filePath).exists()) {
            Glide
                .with(requireContext())
                .load(filePath)
                .into(this)
        }
    }
}

