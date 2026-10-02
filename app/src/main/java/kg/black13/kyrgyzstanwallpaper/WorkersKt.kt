package kg.black13.kyrgyzstanwallpaper

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequest
import androidx.work.WorkManager
import androidx.work.Worker
import androidx.work.WorkerParameters
import com.bumptech.glide.Glide
import com.google.android.gms.tasks.Tasks
import com.google.firebase.firestore.FirebaseFirestore
import java.util.concurrent.TimeUnit

object SchedulerKt {
    private const val AUTO = "auto_wallpaper"
    private const val WEEKLY = "weekly_wallpaper"
    val TYPES = listOf("nature", "animals", "arch", "relig", "stars")

    /** Приводит фоновые задачи в соответствие с настройками */
    fun sync(context: Context) {
        val workManager = WorkManager.getInstance(context)
        val network = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()
        if (PrefsKt.isAutoWallpaper(context)) {
            // Первый запуск сразу после включения, дальше раз в сутки
            val request = PeriodicWorkRequest.Builder(AutoWallpaperWorkerKt::class.java, 24, TimeUnit.HOURS)
                .setConstraints(network)
                .build()
            workManager.enqueueUniquePeriodicWork(AUTO, ExistingPeriodicWorkPolicy.KEEP, request)
        } else {
            workManager.cancelUniqueWork(AUTO)
        }
        if (PrefsKt.isWeekly(context)) {
            val request = PeriodicWorkRequest.Builder(WeeklyWallpaperWorkerKt::class.java, 7, TimeUnit.DAYS)
                .setInitialDelay(7, TimeUnit.DAYS)
                .setConstraints(network)
                .build()
            workManager.enqueueUniquePeriodicWork(WEEKLY, ExistingPeriodicWorkPolicy.KEEP, request)
        } else {
            workManager.cancelUniqueWork(WEEKLY)
        }
    }

    /** Случайная картинка из случайной категории. Вызывать не из главного потока */
    fun randomPicture(exceptUrl: String?): PictureKt? {
        val query = FirebaseFirestore.getInstance().collection(TYPES.random()).orderBy("url").limit(200)
        val pictures = Tasks.await(query.get(), 30, TimeUnit.SECONDS).toObjects(PictureKt::class.java)
        return pickRandom(pictures, exceptUrl)
    }

    fun pickRandom(pictures: List<PictureKt>, exceptUrl: String?): PictureKt? {
        val others = pictures.filter { it.url != exceptUrl }
        return (if (others.isEmpty()) pictures else others).randomOrNull()
    }
}

class AutoWallpaperWorkerKt(context: Context, params: WorkerParameters) : Worker(context, params) {
    override fun doWork(): Result {
        val context = applicationContext
        return try {
            val last = PrefsKt.getLastAutoUrl(context)
            val favorites = FavoritesKt.getAll(context)
            val picture = (if (PrefsKt.isAutoFromFavorites(context) && favorites.isNotEmpty()) {
                SchedulerKt.pickRandom(favorites, last)
            } else {
                SchedulerKt.randomPicture(last)
            }) ?: return Result.retry()
            val bitmap = Glide.with(context).asBitmap().load(picture.url).submit().get()
            if (!PictureActionsKt.setWallpaper(context, bitmap, PictureActionsKt.TARGET_BOTH)) return Result.retry()
            PrefsKt.setLastAutoUrl(context, picture.url)
            Result.success()
        } catch (e: Exception) {
            e.printStackTrace()
            Result.retry()
        }
    }
}

class WeeklyWallpaperWorkerKt(context: Context, params: WorkerParameters) : Worker(context, params) {
    companion object {
        const val CHANNEL = "weekly"
        const val EXTRA_URL = "picture_url"
        const val EXTRA_URL_SMALL = "picture_url_small"
        const val EXTRA_TYPE = "picture_type"
    }

    override fun doWork(): Result {
        val context = applicationContext
        if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(
                context, Manifest.permission.POST_NOTIFICATIONS
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            return Result.success()
        }
        return try {
            val picture = SchedulerKt.randomPicture(null) ?: return Result.retry()
            val bitmap = Glide.with(context).asBitmap().load(picture.url).submit(720, 720).get()

            if (Build.VERSION.SDK_INT >= 26) {
                val channel = NotificationChannel(
                    CHANNEL, context.getString(R.string.weekly_channel), NotificationManager.IMPORTANCE_DEFAULT
                )
                context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
            }
            val intent = Intent(context, MainActivityKt::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                .putExtra(EXTRA_URL, picture.url)
                .putExtra(EXTRA_URL_SMALL, picture.urlSmall)
                .putExtra(EXTRA_TYPE, picture.type)
            val pendingIntent = PendingIntent.getActivity(
                context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            val notification = NotificationCompat.Builder(context, CHANNEL)
                .setSmallIcon(R.drawable.ic_wallpaper)
                .setContentTitle(context.getString(R.string.weekly_title))
                .setContentText(context.getString(R.string.weekly_text))
                .setLargeIcon(bitmap)
                .setStyle(NotificationCompat.BigPictureStyle().bigPicture(bitmap).bigLargeIcon(null as android.graphics.Bitmap?))
                .setContentIntent(pendingIntent)
                .setAutoCancel(true)
                .build()
            NotificationManagerCompat.from(context).notify(1, notification)
            Result.success()
        } catch (e: Exception) {
            e.printStackTrace()
            Result.retry()
        }
    }
}
