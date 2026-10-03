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
            haptics = prefs.getBoolean(KEY_HAPTICS, defaults.haptics),
            hapticStrength = prefs.getFloat(KEY_HAPTIC_STRENGTH, defaults.hapticStrength),
            animationSpeed = prefs.getFloat(KEY_ANIMATION_SPEED, defaults.animationSpeed),
            motionIntensity = prefs.getFloat(KEY_MOTION_INTENSITY, defaults.motionIntensity),
            reduceMotion = prefs.getBoolean(KEY_REDUCE_MOTION, defaults.reduceMotion),
            brightness = prefs.getFloat(KEY_BRIGHTNESS, defaults.brightness),
            deleteCountdownNotification = prefs.getBoolean(KEY_COUNTDOWN, defaults.deleteCountdownNotification),
        )
    }

    fun save(settings: UiSettings) {
        prefs.edit()
            .putInt(KEY_THEME, settings.theme.ordinal)
            .putInt(KEY_ACCENT, settings.accent)
            .putBoolean(KEY_HAPTICS, settings.haptics)
            .putFloat(KEY_HAPTIC_STRENGTH, settings.hapticStrength)
            .putFloat(KEY_ANIMATION_SPEED, settings.animationSpeed)
            .putFloat(KEY_MOTION_INTENSITY, settings.motionIntensity)
            .putBoolean(KEY_REDUCE_MOTION, settings.reduceMotion)
            .putFloat(KEY_BRIGHTNESS, settings.brightness)
            .putBoolean(KEY_COUNTDOWN, settings.deleteCountdownNotification)
            .apply()
    }

    private companion object {
        const val KEY_THEME = "theme"
        const val KEY_ACCENT = "accent"
        const val KEY_HAPTICS = "haptics"
        const val KEY_HAPTIC_STRENGTH = "haptic_strength"
        const val KEY_ANIMATION_SPEED = "animation_speed"
        const val KEY_MOTION_INTENSITY = "motion_intensity"
        const val KEY_REDUCE_MOTION = "reduce_motion"
        const val KEY_BRIGHTNESS = "brightness"
        const val KEY_COUNTDOWN = "delete_countdown_notification"
    }
}
