package com.github.savan.touchlesskiosk.utils

import android.graphics.Bitmap
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.MultiFormatWriter
import com.google.zxing.common.BitMatrix
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel

object QRCodeUtils {
    private const val TAG = "QRCodeUtils"

    fun generateQRCode(data: String, w: Int, h: Int): Bitmap? {
        var bitmap: Bitmap? = null
        try {
            Logger.d(TAG, "encodeBarcodeImage")
            val hints: MutableMap<EncodeHintType, Any> = mutableMapOf()
            hints[EncodeHintType.MARGIN] = 1
            hints[EncodeHintType.CHARACTER_SET] = "UTF-8"
            hints[EncodeHintType.ERROR_CORRECTION] = ErrorCorrectionLevel.L

            val result: BitMatrix = MultiFormatWriter().encode(data, BarcodeFormat.QR_CODE, w, h, hints)

            // {left,top,width,height}
            val offsetRect: IntArray = result.enclosingRectangle ?: let {
                Logger.d(TAG, "encodeBarcodeImage offsetRect is null..")
                return null
            }

            val width = offsetRect[2]
            val height = offsetRect[3]
            val pixels = IntArray(width * height)
            var y = offsetRect[1]
            var y1 = 0
            while (y1 < height) {
                var x = offsetRect[0]
                var x1 = 0
                while (x1 < width) {
                    pixels[y1 * width + x1] = if (result.get(x, y)) 0x000000 else 0xffffff
                    x++
                    x1++
                }
                y++
                y1++
            }
            bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.RGB_565)
            bitmap.setPixels(pixels, 0, width, 0, 0, width, height)
        } catch (e: Exception) {
            Logger.e(TAG, "Exception in encodeBarcodeImage $e")
        }
        return bitmap
    }
}