package de.taz.app.android.util

import androidx.lifecycle.ViewModel

interface StableIdProvider {
    fun getId(key: String): Long
}

class StableIdViewModel: ViewModel(), StableIdProvider {
    private val keyList = mutableListOf<String>()

    override fun getId(key: String): Long {
        var position = keyList.indexOf(key)
        if (position < 0) {
            position = keyList.size
            keyList.add(key)
        }
        return position.toLong()
    }
}


//    override fun onSaveInstanceState(outState: Bundle) {
//        super.onSaveInstanceState(outState)
//        outState.putParcelable(StableIdThing.BUNDLE_KEY, stableIdProvider as StableIdThing)
//    }
//
//    private class StableIdThing() : Parcelable, StableIdProvider {
//        private val keyList = mutableListOf<String>()
//
//        constructor(parcel: Parcel): this() {
//            parcel.readStringList(keyList)
//        }
//
//        override fun getId(key: String): Long {
//            var position = keyList.indexOf(key)
//            if (position < 0) {
//                position = keyList.size
//                keyList.add(key)
//            }
//            return position.toLong()
//        }
//
//        override fun writeToParcel(parcel: Parcel, flags: Int) {
//            parcel.writeStringList(keyList)
//        }
//
//        override fun describeContents(): Int = 0
//
//        companion object CREATOR : Parcelable.Creator<StableIdThing> {
//            override fun createFromParcel(parcel: Parcel): StableIdThing {
//                return StableIdThing(parcel)
//            }
//
//            override fun newArray(size: Int): Array<StableIdThing?> {
//                return arrayOfNulls(size)
//            }
//
//            const val BUNDLE_KEY = "stable_id_thing"
//        }
//    }