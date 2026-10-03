package com.dazaike.photovault.data

import android.content.Context

/** Persists the user-tunable part of [UiSettings] in SharedPreferences. */
class UiSettingsStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("ui_settings", Context.MODE_PRIVATE)

    fun load(): UiSettings {
        val defaults = UiSettings()
        return defaults.copy(
            theme = ThemeMode.entries.getOrElse(prefs.getInt(KEY_THEME, defaults.theme.ordinal)) { defaults.theme },
            accent = prefs.getInt(KEY_ACCENT, defaults.accent),
            deleteCountdownNotification = prefs.getBoolean(KEY_COUNTDOWN, defaults.deleteCountdownNotification),
        )
    }

    fun save(settings: UiSettings) {
        prefs.edit()
            .putInt(KEY_THEME, settings.theme.ordinal)
            .putInt(KEY_ACCENT, settings.accent)
            .putBoolean(KEY_COUNTDOWN, settings.deleteCountdownNotification)
            .apply()
    }

    private companion object {
        const val KEY_THEME = "theme"
        const val KEY_ACCENT = "accent"
        const val KEY_COUNTDOWN = "delete_countdown_notification"
    }
}
