package kg.black13.kyrgyzstanwallpaper

import android.content.Context
import android.content.res.Configuration
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import java.util.Locale

/** Язык интерфейса: русский, кыргызский, английский или как в системе */
object LanguageKt {
    /** "ru", "ky", "en" или "" — как в системе */
    fun current(): String {
        val locales = AppCompatDelegate.getApplicationLocales()
        return if (locales.isEmpty) "" else locales[0]?.language ?: ""
    }

    fun set(context: Context, tag: String) {
        PrefsKt.setLanguage(context, tag)
        AppCompatDelegate.setApplicationLocales(
            if (tag.isEmpty()) LocaleListCompat.getEmptyLocaleList() else LocaleListCompat.forLanguageTags(tag)
        )
    }

    /**
     * Язык мог поменяться в настройках телефона (Android 13+),
     * поэтому при каждом запуске обновляем копию для фоновых задач
     */
    fun remember(context: Context) {
        PrefsKt.setLanguage(context, current())
    }

    /** Контекст с языком приложения для уведомлений: на Android 12 и ниже фоновые задачи его не знают */
    fun localized(context: Context): Context {
        val tag = PrefsKt.getLanguage(context)
        if (tag.isEmpty()) return context
        val config = Configuration(context.resources.configuration)
        config.setLocale(Locale.forLanguageTag(tag))
        return context.createConfigurationContext(config)
    }
}
