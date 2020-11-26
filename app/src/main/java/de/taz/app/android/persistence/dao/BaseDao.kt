package de.taz.app.android.persistence.dao

import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Update

abstract class BaseDao<T> {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract fun insertOrReplace(item: T)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract fun insertOrReplace(items: List<T>)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    abstract fun insertOrAbort(item: T)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    abstract fun insertOrAbort(items: List<T>)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    abstract fun insertOrIgnore(item: T)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    abstract fun insertOrIgnore(items: List<T>)

    @Update
    abstract fun update(item: T)

    @Update
    abstract fun update(items: List<T>)

    @Delete
    abstract fun delete(item: T)

    @Delete
    abstract fun delete(items: List<T>)

}