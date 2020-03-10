package de.taz.app.android.util

/**
 * Singleton base class
 */
open class SingletonHolder<out T, in A>(creator: (A) -> T) {
    private var creator: ((A) -> T)? = creator
    @Volatile private var instance: T? = null

    protected val log by Log

    fun getInstance(arg: A? = null): T {
        if (arg != null && instance == null)
            return createInstance(arg)
        return instance!!
    }

    fun createInstance(arg: A): T {
        val i = instance
        if (i != null) {
            return i
        }

        return synchronized(this) {
            val i2 = instance
            if (i2 != null) {
                i2
            } else {
                val created = creator!!(arg)
                instance = created
                creator = null
                created
            }
        }
    }
}