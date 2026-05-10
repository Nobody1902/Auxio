package org.oxycblt.auxio.image.coil

import android.graphics.Bitmap
import coil3.size.Size
import coil3.transform.Transformation

class ScaleBlurTransformation(
    private val radius: Int = 12,
    private val darkenFactor: Float = 1f
) : Transformation() {
    override val cacheKey: String = "GaussianBlur($radius,${darkenFactor})"

    override suspend fun transform(input: Bitmap, size: Size): Bitmap {
        val scale = (maxOf(input.width, input.height) / 500f).coerceAtLeast(1f)
        val smallW = (input.width / scale).toInt().coerceAtLeast(1)
        val smallH = (input.height / scale).toInt().coerceAtLeast(1)
        val small = Bitmap.createScaledBitmap(input, smallW, smallH, true)
        val blurred = if (radius > 0) {
            gaussianBlur(small, (radius / scale).toInt().coerceAtLeast(1))
        } else small
        val result = Bitmap.createScaledBitmap(blurred, input.width, input.height, true)
        if (blurred != input) blurred.recycle()
        if (small != input) small.recycle()
        return result
    }

    private fun gaussianBlur(bitmap: Bitmap, radius: Int): Bitmap {
        val w = bitmap.width
        val h = bitmap.height
        val pixels = IntArray(w * h)
        bitmap.getPixels(pixels, 0, w, 0, 0, w, h)

        val kernelSize = radius * 2 + 1
        val sigma = radius / 3f
        val kernel = FloatArray(kernelSize)
        var sum = 0f
        for (i in 0 until kernelSize) {
            val x = i - radius
            kernel[i] = kotlin.math.exp(-(x * x) / (2 * sigma * sigma)).toFloat()
            sum += kernel[i]
        }
        for (i in 0 until kernelSize) {
            kernel[i] /= sum
        }

        val temp = IntArray(w * h)

        for (y in 0 until h) {
            for (x in 0 until w) {
                var ra = 0f; var g = 0f; var b = 0f; var a = 0f
                for (k in 0 until kernelSize) {
                    val px = (x + k - radius).coerceIn(0, w - 1)
                    val pixel = pixels[y * w + px]
                    val wgt = kernel[k]
                    a += android.graphics.Color.alpha(pixel) * wgt
                    ra += android.graphics.Color.red(pixel) * wgt
                    g += android.graphics.Color.green(pixel) * wgt
                    b += android.graphics.Color.blue(pixel) * wgt
                }
                temp[y * w + x] = android.graphics.Color.argb(
                    (a + 0.5f).toInt().coerceIn(0, 255),
                    (ra + 0.5f).toInt().coerceIn(0, 255),
                    (g + 0.5f).toInt().coerceIn(0, 255),
                    (b + 0.5f).toInt().coerceIn(0, 255),
                )
            }
        }

        for (y in 0 until h) {
            for (x in 0 until w) {
                var ra = 0f; var g = 0f; var b = 0f; var a = 0f
                for (k in 0 until kernelSize) {
                    val py = (y + k - radius).coerceIn(0, h - 1)
                    val pixel = temp[py * w + x]
                    val wgt = kernel[k]
                    a += android.graphics.Color.alpha(pixel) * wgt
                    ra += android.graphics.Color.red(pixel) * wgt
                    g += android.graphics.Color.green(pixel) * wgt
                    b += android.graphics.Color.blue(pixel) * wgt
                }
                ra *= darkenFactor
                g *= darkenFactor
                b *= darkenFactor
                pixels[y * w + x] = android.graphics.Color.argb(
                    (a + 0.5f).toInt().coerceIn(0, 255),
                    (ra + 0.5f).toInt().coerceIn(0, 255),
                    (g + 0.5f).toInt().coerceIn(0, 255),
                    (b + 0.5f).toInt().coerceIn(0, 255),
                )
            }
        }

        val result = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        result.setPixels(pixels, 0, w, 0, 0, w, h)
        return result
    }
}
