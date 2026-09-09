package de.taz.app.android.monkey

import android.content.ContextWrapper
import android.view.View
import androidx.activity.ComponentActivity
import androidx.annotation.MainThread
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelLazy
import androidx.lifecycle.ViewModelProvider.Factory
import androidx.lifecycle.findViewTreeLifecycleOwner
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewmodel.CreationExtras
import kotlinx.coroutines.CoroutineScope

/*
 * These functions are used to set the default insets by either the system bars or the displayCutout
 * Use this function to fix visual problems
 */
fun View.setDefaultInsets(
    top: Boolean = true,
    bottom: Boolean = true,
    left: Boolean = true,
    right: Boolean = true,
    @WindowInsetsCompat.Type.InsetsType insetsType: Int = WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout()
) {
    // Set Listener
    ViewCompat.setOnApplyWindowInsetsListener(this) { v, insets ->
        val bars = insets.getInsets(insetsType)
        v.updatePadding(
            left = if (left) bars.left else v.paddingLeft,
            top = if (top) bars.top else v.paddingTop,
            right = if (right) bars.right else paddingRight,
            bottom = if (bottom) bars.bottom else paddingBottom,
        )
        insets
    }
}

fun View.setDefaultTopInset() {
    setDefaultInsets(bottom = false, left = false, right = false)
}

fun View.setDefaultBottomInset() {
    setDefaultInsets(top = false, left = false, right = false)
}

fun View.setDefaultHorizontalInsets(
    @WindowInsetsCompat.Type.InsetsType insetsType: Int = WindowInsetsCompat.Type.systemBars()
) {
    setDefaultInsets(top = false, bottom = false, insetsType = insetsType)
}

fun View.setDefaultVerticalInsets() {
    setDefaultInsets(left = false, right = false)
}

fun View.getVisibleHeight(): Int {
    return (this.height - this.translationY).toInt()
}

/**
 * Set a delayed [View.OnClickListener] to ensure multiple clicks don't trigger every time.
 * The execution of [listenerFun] will be prevented for [delay] after a click, then it can be
 * triggered again.
 */
fun View.setOnTapListener(delay: Long = 300L, listenerFun: (View) -> Unit) {
    setOnClickListener {
        if (!isClickable) {
            return@setOnClickListener
        }

        isClickable = false
        listenerFun(this)
        postDelayed({ isClickable = true }, delay)
    }
}

fun View.lifecycleScope(): CoroutineScope? = findViewTreeLifecycleOwner()?.lifecycleScope

fun View.getActivity(): ComponentActivity? {
    var context = getContext()
    while (context is ContextWrapper) {
        if (context is ComponentActivity) {
            return context
        }
        context = context.baseContext
    }
    return null
}

fun View.requireActivity(): ComponentActivity = requireNotNull(getActivity())


@MainThread
inline fun <reified VM : ViewModel> View.activityViewModels(
    noinline extrasProducer: (() -> CreationExtras)? = null,
    noinline factoryProducer: (() -> Factory)? = null
): Lazy<VM> {
    val factoryPromise = factoryProducer ?: {
        requireActivity().defaultViewModelProviderFactory
    }

    return ViewModelLazy(
        VM::class,
        { requireActivity().viewModelStore },
        factoryPromise,
        { extrasProducer?.invoke() ?: requireActivity().defaultViewModelCreationExtras }
    )
}


