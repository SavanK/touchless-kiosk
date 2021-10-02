package com.github.savan.touchlesskiosk.webrtc.model

import android.os.Parcel
import android.os.Parcelable

class Customer(val customerId: String): Parcelable {
    constructor(parcel: Parcel) : this(parcel.readString()!!)

    override fun writeToParcel(parcel: Parcel, flags: Int) {
        parcel.writeString(customerId)
    }

    override fun describeContents() = 0

    companion object CREATOR : Parcelable.Creator<Customer> {
        override fun createFromParcel(parcel: Parcel): Customer {
            return Customer(parcel)
        }

        override fun newArray(size: Int): Array<Customer?> {
            return arrayOfNulls(size)
        }
    }

    override fun toString(): String {
        return "Customer{id: $customerId}"
    }

    override fun equals(other: Any?): Boolean {
        return when (other) {
            null -> { false }
            !is Customer -> { false }
            else -> {
                customerId == other.customerId
            }
        }
    }
}