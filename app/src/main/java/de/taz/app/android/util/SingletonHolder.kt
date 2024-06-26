package de.taz.app.android.util

import androidx.annotation.VisibleForTesting

/**
 * Singleton base class
 */
open class SingletonHolder<T, in A>(private val creator: (A) -> T) {
    @Volatile private var instance: T? = null

    protected val log by Log

    fun getInstance(arg: A): T {
        if (instance == null)
            return createInstance(arg)
        return instance!!
    }

    private fun createInstance(arg: A): T {
        val i = instance
        if (i != null) {
            return i
        }

        return synchronized(this) {
            val i2 = instance
            if (i2 != null) {
                i2
            } else {
                val created = creator(arg)
                instance = created
                created
            }
        }
    }

    @VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
    fun inject(injectedInstance: T?) {
        this.instance = injectedInstance
    }
}