// IStreamListener.aidl
package com.github.savan.touchlesskiosk;

interface IStreamListener {
    const int STATUS_READY_TO_STREAM = 0;
    const int STATUS_NOT_READY_TO_STREAM = -1;
    const int STATUS_STREAMING = 1;

    void onStatusChange(int status);
}