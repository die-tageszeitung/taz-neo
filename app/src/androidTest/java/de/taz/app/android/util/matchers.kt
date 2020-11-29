package de.taz.app.android.util

import org.mockito.Mockito

/**
 * any() implementation that is compatible with kotlin typechecker
 */
fun <T> any(type: Class<T>): T = Mockito.any(type)