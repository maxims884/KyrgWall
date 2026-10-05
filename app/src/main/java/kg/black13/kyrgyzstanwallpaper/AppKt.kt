package kg.black13.kyrgyzstanwallpaper

import android.app.Application
import androidx.appcompat.app.AppCompatDelegate

class AppKt : Application() {
    override fun onCreate() {
        super.onCreate()
        // Тему нужно выставить до создания активити, иначе она пересоздастся на старте
        AppCompatDelegate.setDefaultNightMode(PrefsKt.getNightMode(this))
    }
}
