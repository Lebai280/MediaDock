package com.lovelev.mediadock.ui.detail

import android.graphics.BitmapFactory
import androidx.compose.ui.graphics.Color
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.CancellationException
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * 海报取色：拿**底部一条**的平均色。详情页下半部分的背景就用它接出去 ——
 * 整页看上去是那张海报一路延伸下来，而不是图片下面另起一块背景。
 *
 * 为什么不复用 Coil 已经加载好的那张：那层的 bitmap 拿不到（各实现的图像类型不同），
 * 而这里单独下一张 w92（几 KB）比跟 Coil 内部较劲省事。失败返回 null，界面走兜底，
 * 拿不到颜色不影响任何功能。
 */
internal object PosterTint {

    /** 取色用的那条带子占整图高度的比例。 */
    private const val BAND = 0.12f

    /** [url] 底部一条的平均色；网络 / 解码 / 失败一律返回 null。 */
    suspend fun bottomAverage(url: String, client: OkHttpClient): Color? =
        withContext(Dispatchers.IO) {
            val bytes = try {
                client.newCall(Request.Builder().url(url).build()).execute().use { resp ->
                    if (!resp.isSuccessful) return@withContext null
                    resp.body.bytes()
                }
            } catch (e: CancellationException) {
                throw e   // 取消要原样抛出，别当成业务失败吞掉
            } catch (e: Exception) {
                return@withContext null
            }

            val bitmap = try {
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return@withContext null
            } catch (e: CancellationException) {
                throw e   // 取消要原样抛出，别当成业务失败吞掉
            } catch (e: Exception) {
                return@withContext null
            }

            try {
                if (bitmap.width <= 0 || bitmap.height <= 0) return@withContext null
                val top = (bitmap.height * (1f - BAND)).toInt().coerceIn(0, bitmap.height - 1)
                var r = 0L
                var g = 0L
                var b = 0L
                var n = 0L
                for (y in top until bitmap.height) {
                    for (x in 0 until bitmap.width) {
                        val c = bitmap.getPixel(x, y)
                        r += (c shr 16) and 0xFF
                        g += (c shr 8) and 0xFF
                        b += c and 0xFF
                        n++
                    }
                }
                if (n == 0L) {
                    null
                } else {
                    Color(
                        red = (r / n).toInt() / 255f,
                        green = (g / n).toInt() / 255f,
                        blue = (b / n).toInt() / 255f,
                    )
                }
            } finally {
                bitmap.recycle()
            }
        }
}
