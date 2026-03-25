package de.taz.app.android.monkey

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.runningFold

data class WithPreviousValue<T>(
    val current: T,
    val previous: T?,
)

fun <T> Flow<T>.withPreviousValue(): Flow<WithPreviousValue<T>> =
    runningFold(
        initial = null as (WithPreviousValue<T>?),
        operation = { accumulator, new -> WithPreviousValue(
            new,
            accumulator?.current)
        }
    ).filterNotNull()
