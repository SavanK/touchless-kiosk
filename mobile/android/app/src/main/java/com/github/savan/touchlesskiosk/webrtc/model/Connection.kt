package com.github.savan.touchlesskiosk.webrtc.model

import android.os.Parcel
import android.os.Parcelable

class Connection(val customer: Customer, val kiosk: Kiosk): Parcelable {
    constructor(parcel: Parcel) : this(
        parcel.readParcelable<Parcelable>(Customer::class.java.classLoader) as Customer,
        parcel.readParcelable<Parcelable>(Kiosk::class.java.classLoader) as Kiosk
    )

    override fun writeToParcel(parcel: Parcel, flags: Int) {
        parcel.writeParcelable(customer, 0)
        parcel.writeParcelable(kiosk, 0)
    }

    override fun describeContents(): Int {
        return 0
    }

    companion object CREATOR : Parcelable.Creator<Connection> {
        override fun createFromParcel(parcel: Parcel): Connection {
            return Connection(parcel)
        }

        override fun newArray(size: Int): Array<Connection?> {
            return arrayOfNulls(size)
        }
    }

    override fun toString(): String {
        return "Connection{kiosk: $kiosk, customer: $customer}"
    }

    override fun equals(other: Any?): Boolean {
        return when (other) {
            null -> { false }
            !is Connection -> { false }
            else -> {
                kiosk == other.kiosk && customer == other.customer
            }
        }
    }
}