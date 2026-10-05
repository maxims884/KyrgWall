package kg.black13.kyrgyzstanwallpaper

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ListenableWorker
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequest
import androidx.work.WorkManager
import androidx.work.Worker
import androidx.work.WorkerParameters
import com.bumptech.glide.Glide
import java.util.concurrent.TimeUnit

object SchedulerKt {
    private const val AUTO = "auto_wallpaper"
    private const val WEEKLY = "weekly_wallpaper"
    private const val REMINDER = "reminder"

    /** Приводит фоновые задачи в соответствие с настройками */
    fun sync(context: Context) {
        // Автосмена: первый запуск сразу после включения, дальше раз в сутки
        schedule(context, AUTO, PrefsKt.isAutoWallpaper(context), AutoWallpaperWorkerKt::class.java, 1, 0)
        schedule(context, WEEKLY, PrefsKt.isWeekly(context), WeeklyWallpaperWorkerKt::class.java, 7, 7)
        // Раз в сутки проверяем, давно ли пользователь заходил
        schedule(context, REMINDER, PrefsKt.isReminder(context), ReminderWorkerKt::class.java, 1, 1)
    }

    private fun schedule(
        context: Context, name: String, enabled: Boolean,
        worker: Class<out ListenableWorker>, everyDays: Long, delayDays: Long
    ) {
        val workManager = WorkManager.getInstance(context)
        if (!enabled) {
            workManager.cancelUniqueWork(name)
            return
        }
        val network = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()
        val request = PeriodicWorkRequest.Builder(worker, everyDays, TimeUnit.DAYS)
            .setInitialDelay(delayDays, TimeUnit.DAYS)
            .setConstraints(network)
            .build()
        workManager.enqueueUniquePeriodicWork(name, ExistingPeriodicWorkPolicy.KEEP, request)
    }

    /** Случайные обои (не открытка). Вызывать не из главного потока */
    fun randomWallpaper(context: Context, exceptUrl: String?): PictureKt? {
        val wallpapers = CatalogKt.loadBlocking(context).filter { it.type in CatalogKt.WALLPAPER_TYPES }
        return pickRandom(wallpapers, exceptUrl)
    }

    fun pickRandom(pictures: List<PictureKt>, exceptUrl: String?): PictureKt? {
        val others = pictures.filter { it.url != exceptUrl }
        return (if (others.isEmpty()) pictures else others).randomOrNull()
    }
}

object NotificationsKt {
    const val EXTRA_URL = "picture_url"
    const val EXTRA_URL_SMALL = "picture_url_small"
    const val EXTRA_TYPE = "picture_type"
    // Если человек не заходит дольше месяца, перестаём его беспокоить совсем
    private val GIVE_UP_AFTER = TimeUnit.DAYS.toMillis(30)

    fun isAllowed(context: Context): Boolean {
        if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(
                context, Manifest.permission.POST_NOTIFICATIONS
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            return false
        }
        val lastOpen = PrefsKt.getLastOpen(context)
        return lastOpen == 0L || System.currentTimeMillis() - lastOpen < GIVE_UP_AFTER
    }

    /** Уведомление с картинкой; нажатие открывает её в приложении. Вызывать не из главного потока */
    fun showPicture(appContext: Context, id: Int, channel: String, channelName: Int, title: Int, text: Int): Boolean {
        val picture = SchedulerKt.randomWallpaper(appContext, null) ?: return false
        val bitmap = Glide.with(appContext).asBitmap().load(picture.url).submit(720, 720).get()
        // Тексты уведомления на языке, выбранном в приложении
        val context = LanguageKt.localized(appContext)

        if (Build.VERSION.SDK_INT >= 26) {
            // Без звука: это не срочные уведомления
            val notificationChannel = NotificationChannel(
                channel, context.getString(channelName), NotificationManager.IMPORTANCE_LOW
            )
            context.getSystemService(NotificationManager::class.java).createNotificationChannel(notificationChannel)
        }
        val intent = Intent(context, MainActivityKt::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            .putExtra(EXTRA_URL, picture.url)
            .putExtra(EXTRA_URL_SMALL, picture.urlSmall)
            .putExtra(EXTRA_TYPE, picture.type)
        val pendingIntent = PendingIntent.getActivity(
            context, id, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = NotificationCompat.Builder(context, channel)
            .setSmallIcon(R.drawable.ic_wallpaper)
            .setContentTitle(context.getString(title))
            .setContentText(context.getString(text))
            .setLargeIcon(bitmap)
            .setStyle(NotificationCompat.BigPictureStyle().bigPicture(bitmap).bigLargeIcon(null as Bitmap?))
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setAutoCancel(true)
            .build()
        NotificationManagerCompat.from(context).notify(id, notification)
        PrefsKt.markNotified(context)
        return true
    }
}

class AutoWallpaperWorkerKt(context: Context, params: WorkerParameters) : Worker(context, params) {
    override fun doWork(): Result {
        val context = applicationContext
        return try {
            val last = PrefsKt.getLastAutoUrl(context)
            val favorites = FavoritesKt.getAll(context).filter { it.type != CatalogKt.CARDS }
            val picture = (if (PrefsKt.isAutoFromFavorites(context) && favorites.isNotEmpty()) {
                SchedulerKt.pickRandom(favorites, last)
            } else {
                SchedulerKt.randomWallpaper(context, last)
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
    override fun doWork(): Result {
        val context = applicationContext
        if (!NotificationsKt.isAllowed(context)) return Result.success()
        return try {
            val shown = NotificationsKt.showPicture(
                context, 1, "weekly", R.string.weekly_channel, R.string.weekly_title, R.string.weekly_text
            )
            if (shown) Result.success() else Result.retry()
        } catch (e: Exception) {
            e.printStackTrace()
            Result.retry()
        }
    }
}

/**
 * Напоминание тем, кто давно не заходил: одно через 5 дней и ещё одно через 14.
 * Счётчик сбрасывается при каждом открытии приложения.
 */
class ReminderWorkerKt(context: Context, params: WorkerParameters) : Worker(context, params) {
    companion object {
        private val FIRST_AFTER = TimeUnit.DAYS.toMillis(5)
        private val SECOND_AFTER = TimeUnit.DAYS.toMillis(14)
        // Не чаще одного нашего уведомления в три дня, считая "Обои недели"
        private val MIN_GAP = TimeUnit.DAYS.toMillis(3)
    }

    override fun doWork(): Result {
        val context = applicationContext
        val now = System.currentTimeMillis()
        val lastOpen = PrefsKt.getLastOpen(context)
        if (lastOpen == 0L || !NotificationsKt.isAllowed(context)) return Result.success()

        val idle = now - lastOpen
        val sent = PrefsKt.getRemindersSent(context)
        val due = (sent == 0 && idle >= FIRST_AFTER) || (sent == 1 && idle >= SECOND_AFTER)
        if (!due || now - PrefsKt.getLastNotified(context) < MIN_GAP) return Result.success()

        return try {
            val shown = NotificationsKt.showPicture(
                context, 2, "reminder", R.string.reminder_channel, R.string.reminder_title, R.string.reminder_text
            )
            if (shown) PrefsKt.setRemindersSent(context, sent + 1)
            Result.success()
        } catch (e: Exception) {
            e.printStackTrace()
            Result.success()
        }
    }
}
