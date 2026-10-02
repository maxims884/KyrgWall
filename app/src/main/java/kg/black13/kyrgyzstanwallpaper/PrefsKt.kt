package kg.black13.kyrgyzstanwallpaper

import android.content.Context

/** Настройки автосмены обоев и уведомлений. Читаются и из фоновых задач, поэтому без ManagerKt */
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

    fun getLastAutoUrl(context: Context) = sp(context).getString("lastAutoUrl", null)
    fun setLastAutoUrl(context: Context, value: String?) =
        sp(context).edit().putString("lastAutoUrl", value).apply()

    fun isNotificationsAsked(context: Context) = sp(context).getBoolean("notificationsAsked", false)
    fun setNotificationsAsked(context: Context) =
        sp(context).edit().putBoolean("notificationsAsked", true).apply()
}
