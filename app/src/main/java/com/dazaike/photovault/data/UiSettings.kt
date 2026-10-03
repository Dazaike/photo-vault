package com.dazaike.photovault.data

enum class ThemeMode { System, Light, Dark }

/**
 * Appearance, motion and haptic preferences. Ranges: [hapticStrength] 0.25..1,
 * [animationSpeed] 0.5..2, [motionIntensity] 0..1, [brightness] 0.5..1.5.
 */
data class UiSettings(
    val theme: ThemeMode = ThemeMode.System,
    val accent: Int = 0xFFFF9F0A.toInt(),
    val haptics: Boolean = true,
    val hapticStrength: Float = 0.75f,
    val animationSpeed: Float = 1f,
    val motionIntensity: Float = 1f,
    val reduceMotion: Boolean = false,
    val brightness: Float = 1f,
    /** Show a live countdown notification while an exported file waits to be auto-deleted. */
    val deleteCountdownNotification: Boolean = false,
)
