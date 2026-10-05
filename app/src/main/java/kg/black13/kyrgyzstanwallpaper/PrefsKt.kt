package kg.black13.kyrgyzstanwallpaper

import android.content.Context
import androidx.appcompat.app.AppCompatDelegate

/** Настройки автосмены обоев, уведомлений и темы. Читаются и из фоновых задач, поэтому без ManagerKt */
object PrefsKt {
    private fun sp(context: Context) =
        context.applicationContext.getSharedPreferences("Settings", Context.MODE_PRIVATE)

    fun isAutoWallpaper(context: Context) = sp(context).getBoolean("autoWallpaper", false)
    fun setAutoWallpaper(context: Context, value: Boolean) =
        sp(context).edit().putBoolean("autoWallpaper", value).apply()

    fun isAutoFromFavorites(context: Context) = sp(context).getBoolean("autoFromFavorites", true)
    fun setAutoFromFavorites(context: Context, value: Boolean) =
        sp(context).edit().putBoolean("autoFromFavorites", value).apply()

    fun isWeekly(context: Context) = sp(context).getBoolean("weekly", true)
    fun setWeekly(context: Context, value: Boolean) =
        sp(context).edit().putBoolean("weekly", value).apply()

    fun isReminder(context: Context) = sp(context).getBoolean("reminder", true)
    fun setReminder(context: Context, value: Boolean) =
        sp(context).edit().putBoolean("reminder", value).apply()

    fun isHolidays(context: Context) = sp(context).getBoolean("holidays", true)
    fun setHolidays(context: Context, value: Boolean) =
        sp(context).edit().putBoolean("holidays", value).apply()

    // Ключ "праздник_дата", про который уже напомнили
    fun isHolidayNotified(context: Context, key: String) =
        sp(context).getStringSet("holidaysNotified", emptySet())!!.contains(key)
    fun setHolidayNotified(context: Context, key: String) {
        val keys = HashSet(sp(context).getStringSet("holidaysNotified", emptySet())!!)
        keys.add(key)
        sp(context).edit().putStringSet("holidaysNotified", keys).apply()
    }

    fun getLastAutoUrl(context: Context) = sp(context).getString("lastAutoUrl", null)
    fun setLastAutoUrl(context: Context, value: String?) =
        sp(context).edit().putString("lastAutoUrl", value).apply()

    fun isNotificationsAsked(context: Context) = sp(context).getBoolean("notificationsAsked", false)
    fun setNotificationsAsked(context: Context) =
        sp(context).edit().putBoolean("notificationsAsked", true).apply()

    /** Одно из AppCompatDelegate.MODE_NIGHT_*: как в системе, светлая или тёмная */
    fun getNightMode(context: Context) =
        sp(context).getInt("nightMode", AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
    fun setNightMode(context: Context, value: Int) =
        sp(context).edit().putInt("nightMode", value).apply()

    /**
     * Язык, выбранный в приложении: "ru", "ky", "en" или "" (как в системе).
     * Сам язык интерфейса хранит AppCompat, а эта копия нужна уведомлениям из фоновых задач
     */
    fun getLanguage(context: Context) = sp(context).getString("language", "") ?: ""
    fun setLanguage(context: Context, value: String) =
        sp(context).edit().putString("language", value).apply()

    // Когда пользователь последний раз открывал приложение и сколько напоминаний получил с тех пор
    fun getLastOpen(context: Context) = sp(context).getLong("lastOpen", 0)
    fun getRemindersSent(context: Context) = sp(context).getInt("remindersSent", 0)
    fun setRemindersSent(context: Context, value: Int) =
        sp(context).edit().putInt("remindersSent", value).apply()
    fun markOpened(context: Context) =
        sp(context).edit().putLong("lastOpen", System.currentTimeMillis()).putInt("remindersSent", 0).apply()

    // Время последнего нашего уведомления, чтобы они не шли одно за другим
    fun getLastNotified(context: Context) = sp(context).getLong("lastNotified", 0)
    fun markNotified(context: Context) =
        sp(context).edit().putLong("lastNotified", System.currentTimeMillis()).apply()
}
