package com.github.savan.touchlesskiosk.webrtc.model

import android.os.Parcel
import android.os.Parcelable

class Kiosk(val kioskId: String): Parcelable {
    constructor(parcel: Parcel) : this(parcel.readString()!!)

    override fun describeContents() = 0

    override fun writeToParcel(parcel: Parcel, flags: Int) {
        parcel.writeString(kioskId)
    }

    companion object CREATOR : Parcelable.Creator<Kiosk> {
        override fun createFromParcel(parcel: Parcel): Kiosk {
            return Kiosk(parcel)
        }

        override fun newArray(size: Int): Array<Kiosk?> {
            return arrayOfNulls(size)
        }
    }

    override fun toString(): String {
        return "Kiosk{id: $kioskId}"
    }

    override fun equals(other: Any?): Boolean {
        return when (other) {
            null -> { false }
            !is Kiosk -> { false }
            else -> {
                kioskId == other.kioskId
            }
        }
    }
}