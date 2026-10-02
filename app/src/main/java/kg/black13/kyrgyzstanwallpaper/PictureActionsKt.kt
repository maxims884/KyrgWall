package kg.black13.kyrgyzstanwallpaper

import android.app.WallpaperManager
import android.content.ClipData
import android.graphics.Bitmap
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.drawable.Drawable
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.core.content.FileProvider
import com.bumptech.glide.Glide
import com.bumptech.glide.request.target.CustomTarget
import com.bumptech.glide.request.transition.Transition
import java.io.File
import java.io.IOException

object PictureActionsKt {
    private const val ALBUM = "Kyrgyzstan"
    const val TARGET_HOME = 0
    const val TARGET_LOCK = 1
    const val TARGET_BOTH = 2

    fun isLockScreenSupported(context: Context): Boolean {
        return Build.VERSION.SDK_INT >= 24 && WallpaperManager.getInstance(context).isSetWallpaperAllowed
    }

    /** Вызывать не из главного потока */
    fun setWallpaper(context: Context, bitmap: Bitmap, target: Int): Boolean {
        val wallpaperManager = WallpaperManager.getInstance(context)
        return try {
            if (Build.VERSION.SDK_INT >= 24) {
                val flags = when (target) {
                    TARGET_HOME -> WallpaperManager.FLAG_SYSTEM
                    TARGET_LOCK -> WallpaperManager.FLAG_LOCK
                    else -> WallpaperManager.FLAG_SYSTEM or WallpaperManager.FLAG_LOCK
                }
                wallpaperManager.setBitmap(bitmap, null, true, flags)
            } else {
                wallpaperManager.setBitmap(bitmap)
            }
            true
        } catch (e: IOException) {
            e.printStackTrace()
            false
        }
    }

    /** Скачивает оригинал картинки (или берёт из кэша Glide) и отдаёт файл в главном потоке */
    fun loadFile(context: Context, url: String?, onReady: (File?) -> Unit) {
        Glide.with(context.applicationContext).downloadOnly().load(url)
            .into(object : CustomTarget<File>() {
                override fun onResourceReady(resource: File, transition: Transition<in File>?) {
                    onReady(resource)
                }

                override fun onLoadFailed(errorDrawable: Drawable?) {
                    onReady(null)
                }

                override fun onLoadCleared(placeholder: Drawable?) {}
            })
    }

    /** Вызывать не из главного потока */
    fun saveToGallery(context: Context, source: File, url: String?): Boolean {
        val name = "kyrgyzstan_" + System.currentTimeMillis() + "." + extension(url)
        return try {
            if (Build.VERSION.SDK_INT >= 29) {
                val resolver = context.contentResolver
                val values = ContentValues()
                values.put(MediaStore.Images.Media.DISPLAY_NAME, name)
                values.put(MediaStore.Images.Media.MIME_TYPE, mimeType(url))
                values.put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/" + ALBUM)
                values.put(MediaStore.Images.Media.IS_PENDING, 1)
                val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values) ?: return false
                val out = resolver.openOutputStream(uri)
                if (out == null) {
                    resolver.delete(uri, null, null)
                    return false
                }
                out.use { source.inputStream().use { input -> input.copyTo(it) } }
                values.clear()
                values.put(MediaStore.Images.Media.IS_PENDING, 0)
                resolver.update(uri, values, null, null)
            } else {
                val dir = File(
                    Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), ALBUM
                )
                dir.mkdirs()
                val target = source.copyTo(File(dir, name), true)
                MediaScannerConnection.scanFile(context, arrayOf(target.absolutePath), arrayOf(mimeType(url)), null)
            }
            true
        } catch (e: IOException) {
            e.printStackTrace()
            false
        }
    }

    /** Вызывать не из главного потока. Копирует картинку в кэш и возвращает Intent для отправки */
    fun shareIntent(context: Context, source: File, url: String?): Intent? {
        return try {
            val dir = File(context.cacheDir, "shared")
            dir.mkdirs()
            val target = source.copyTo(File(dir, "kyrgyzstan." + extension(url)), true)
            val uri: Uri = FileProvider.getUriForFile(context, context.packageName + ".fileprovider", target)
            val intent = Intent(Intent.ACTION_SEND)
            intent.type = mimeType(url)
            intent.putExtra(Intent.EXTRA_STREAM, uri)
            intent.putExtra(Intent.EXTRA_TEXT, context.getString(R.string.share_text))
            intent.clipData = ClipData.newRawUri(null, uri)
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            Intent.createChooser(intent, context.getString(R.string.share_title))
        } catch (e: IOException) {
            e.printStackTrace()
            null
        }
    }

    private fun extension(url: String?): String {
        val name = Uri.parse(url ?: "").lastPathSegment ?: ""
        return if (name.endsWith(".png", true)) "png" else "jpg"
    }

    private fun mimeType(url: String?): String {
        return if (extension(url) == "png") "image/png" else "image/jpeg"
    }
}
