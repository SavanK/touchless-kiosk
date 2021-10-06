package com.github.savan.touchlesskiosk.webrtc.model

import android.os.Parcel
import android.os.Parcelable

class MouseEvent(val mouseEventType: String, val timeStamp: Long, val x: Float, val y: Float): Parcelable {
    constructor(parcel: Parcel) : this(
        parcel.readString()!!,
        parcel.readLong(),
        parcel.readFloat(),
        parcel.readFloat()
    )

    override fun writeToParcel(parcel: Parcel, flags: Int) {
        parcel.writeString(mouseEventType)
        parcel.writeLong(timeStamp)
        parcel.writeFloat(x)
        parcel.writeFloat(y)
    }

    override fun describeContents(): Int {
        return 0
    }

    companion object CREATOR : Parcelable.Creator<MouseEvent> {
        const val MOUSE_DOWN = "mouseDown"
        const val MOUSE_MOVE = "mouseMove"
        const val MOUSE_UP = "mouseUp"

        override fun createFromParcel(parcel: Parcel): MouseEvent {
            return MouseEvent(parcel)
        }

        override fun newArray(size: Int): Array<MouseEvent?> {
            return arrayOfNulls(size)
        }
    }

    override fun toString(): String {
        return "MouseEvent{eventType: $mouseEventType, timestamp: $timeStamp, x=$x, y=$y}"
    }

    override fun equals(other: Any?): Boolean {
        return when (other) {
            null -> { false }
            !is MouseEvent -> { false }
            else -> {
                mouseEventType == other.mouseEventType && timeStamp == other.timeStamp
                        && x== other.x && y == other.y
            }
        }
    }
}