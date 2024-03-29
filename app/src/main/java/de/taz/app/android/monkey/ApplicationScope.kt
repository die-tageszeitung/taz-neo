package de.taz.app.android.monkey

import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.lifecycle.AndroidViewModel
import de.taz.app.android.TazApplication
import kotlinx.coroutines.CoroutineScope

fun Fragment.getApplicationScope(): CoroutineScope =
    (requireActivity().application as TazApplication).applicationScope

fun AppCompatActivity.getApplicationScope(): CoroutineScope =
    (application as TazApplication).applicationScope

fun AndroidViewModel.getApplicationScope(): CoroutineScope =
    getApplication<TazApplication>().applicationScope