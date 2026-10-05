package kg.black13.kyrgyzstanwallpaper

import android.content.Context
import androidx.work.Worker
import androidx.work.WorkerParameters
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

/** Праздники, к которым присылаем уведомление "открытки готовы" */
object HolidaysKt {
    /**
     * dates — "MM-dd" для праздников с постоянной датой или "yyyy-MM-dd" для айтов.
     * Даты айтов считаются по лунному календарю и каждый год объявляются муфтиятом заново,
     * поэтому их надо проверять и дописывать на следующие годы.
     */
    class Holiday(val id: String, val title: Int, val dates: List<String>)

    val LIST = listOf(
        Holiday("new_year", R.string.holiday_new_year, listOf("01-01")),
        Holiday("feb23", R.string.holiday_feb23, listOf("02-23")),
        Holiday("mar8", R.string.holiday_mar8, listOf("03-08")),
        Holiday("nooruz", R.string.holiday_nooruz, listOf("03-21")),
        Holiday("victory", R.string.holiday_victory, listOf("05-09")),
        Holiday("independence", R.string.holiday_independence, listOf("08-31")),
        Holiday(
            "orozo_ait", R.string.holiday_orozo_ait,
            listOf("2026-03-20", "2027-03-10", "2028-02-27", "2029-02-14", "2030-02-05")
        ),
        Holiday(
            "kurman_ait", R.string.holiday_kurman_ait,
            listOf("2026-05-27", "2027-05-16", "2028-05-05", "2029-04-24", "2030-04-13")
        ),
    )

    /** Праздник сегодня или завтра и его дата "yyyy-MM-dd", иначе null */
    fun upcoming(now: Calendar): Pair<Holiday, String>? {
        val format = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        for (shift in 0..1) {
            val day = now.clone() as Calendar
            day.add(Calendar.DAY_OF_YEAR, shift)
            val date = format.format(day.time)
            for (holiday in LIST) {
                if (holiday.dates.any { it == date || it == date.substring(5) }) return holiday to date
            }
        }
        return null
    }
}

/**
 * За день до праздника (или утром в сам праздник) напоминает про открытки.
 * На каждый праздник приходит одно уведомление, и только днём.
 */
class HolidayWorkerKt(context: Context, params: WorkerParameters) : Worker(context, params) {
    override fun doWork(): Result {
        val context = applicationContext
        val now = Calendar.getInstance()
        val hour = now.get(Calendar.HOUR_OF_DAY)
        if (hour < 9 || hour >= 21 || !NotificationsKt.isAllowed(context)) return Result.success()

        val (holiday, date) = HolidaysKt.upcoming(now) ?: return Result.success()
        val key = holiday.id + "_" + date
        if (PrefsKt.isHolidayNotified(context, key)) return Result.success()

        return try {
            val localized = LanguageKt.localized(context)
            // В уведомлении показываем открытку, если они уже есть
            val cards = CatalogKt.loadBlocking(context).filter { it.type == CatalogKt.CARDS }
            val shown = NotificationsKt.show(
                context, 3, "holidays", R.string.holiday_channel,
                localized.getString(R.string.holiday_title, localized.getString(holiday.title)),
                localized.getString(R.string.holiday_text),
                cards.randomOrNull(), CatalogKt.CARDS
            )
            if (shown) PrefsKt.setHolidayNotified(context, key)
            Result.success()
        } catch (e: Exception) {
            e.printStackTrace()
            Result.retry()
        }
    }
}
