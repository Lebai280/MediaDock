package com.lovelev.mediadock.ui.player

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.os.Build
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.view.PixelCopy
import android.view.SurfaceView
import android.view.View
import android.view.ViewGroup
import com.lovelev.mediadock.data.log.AppLog

/**
 * 截图：`PixelCopy` 从画面那层取一帧，存进相册（Pictures/MediaDock）。
 * 回来一句话（成功 / 失败）给界面提示。
 */
internal fun captureFrame(context: Context, root: View?, onDone: (String) -> Unit) {
    val surfaceView = root?.findSurfaceView()
    if (surfaceView == null || surfaceView.width <= 0 || surfaceView.height <= 0) {
        onDone("截图失败：拿不到画面")
        return
    }
    val bitmap = Bitmap.createBitmap(surfaceView.width, surfaceView.height, Bitmap.Config.ARGB_8888)
    try {
        PixelCopy.request(
            surfaceView,
            bitmap,
            { result ->
                if (result != PixelCopy.SUCCESS) {
                    AppLog.d("player", "PixelCopy failed: $result")
                    onDone("截图失败")
                    return@request
                }
                onDone(saveToGallery(context, bitmap))
            },
            Handler(Looper.getMainLooper()),
        )
    } catch (e: Exception) {
        AppLog.d("player", "PixelCopy threw: ${e.message}")
        onDone("截图失败：${e.message}")
    }
}

/** 存进相册。API 29+ 走 MediaStore 的 scoped storage，不需要存储权限。 */
private fun saveToGallery(context: Context, bitmap: Bitmap): String {
    val name = "Mooring_${System.currentTimeMillis()}.png"
    val values = ContentValues().apply {
        put(MediaStore.Images.Media.DISPLAY_NAME, name)
        put(MediaStore.Images.Media.MIME_TYPE, "image/png")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/Mooring")
        }
    }
    return try {
        val uri = context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
            ?: return "截图保存失败"
        context.contentResolver.openOutputStream(uri)?.use { out ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
        }
        "已保存到相册"
    } catch (e: Exception) {
        AppLog.d("player", "saveToGallery failed: ${e.message}")
        "截图保存失败：${e.message}"
    } finally {
        bitmap.recycle()
    }
}

/** 从 View 树里找出承载视频的那层 SurfaceView。 */
private fun View.findSurfaceView(): SurfaceView? {
    if (this is SurfaceView) return this
    if (this is ViewGroup) {
        for (i in 0 until childCount) {
            getChildAt(i).findSurfaceView()?.let { return it }
        }
    }
    return null
}
