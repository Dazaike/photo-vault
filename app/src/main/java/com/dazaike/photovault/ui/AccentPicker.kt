package com.dazaike.photovault.ui

import androidx.compose.animation.core.animateFloatAsState
import com.dazaike.photovault.ui.theme.LocalMotion
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberUpdatedState
import kotlinx.coroutines.delay
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kyant.backdrop.Backdrop
import com.dazaike.photovault.ui.theme.DefaultAccent
import com.dazaike.photovault.ui.theme.Prism
import com.dazaike.photovault.ui.theme.PrismText

private class Preset(val name: String, val color: Color)

private val Presets = listOf(
    Preset("Orange", DefaultAccent),
    Preset("Red", Color(0xFFFF453A)),
    Preset("Pink", Color(0xFFFF375F)),
    Preset("Purple", Color(0xFFBF5AF2)),
    Preset("Blue", Color(0xFF0A84FF)),
    Preset("Cyan", Color(0xFF64D2FF)),
    Preset("Green", Color(0xFF30D158)),
    Preset("Yellow", Color(0xFFFFD60A)),
)

private const val MIN_BRIGHTNESS = 0.35f

/**
 * Accent picker: preset swatches plus hue / saturation / brightness sliders with live gradient
 * tracks and preview. Slider drags apply to the app on release; presets and Reset apply immediately.
 */
@Composable
fun AccentPicker(
    backdrop: Backdrop,
    accent: Color,
    onAccent: (Color) -> Unit,
) {
    val initial = remember {
        FloatArray(3).also { android.graphics.Color.colorToHSV(accent.toArgb(), it) }
    }
    var hue by remember { mutableFloatStateOf(initial[0] / 360f) }
    var saturation by remember { mutableFloatStateOf(initial[1]) }
    var shade by remember { mutableFloatStateOf(((initial[2] - MIN_BRIGHTNESS) / (1f - MIN_BRIGHTNESS)).coerceIn(0f, 1f)) }

    fun brightness() = MIN_BRIGHTNESS + (1f - MIN_BRIGHTNESS) * shade
    fun current() = Color.hsv(hue * 360f, saturation, brightness())
    val haptics = LocalHaptics.current
    var dirty by remember { mutableStateOf(false) }
    fun select(color: Color) {
        haptics.perform(HapticKind.Tick)
        val hsv = FloatArray(3).also { android.graphics.Color.colorToHSV(color.toArgb(), it) }
        hue = hsv[0] / 360f
        saturation = hsv[1]
        shade = ((hsv[2] - MIN_BRIGHTNESS) / (1f - MIN_BRIGHTNESS)).coerceIn(0f, 1f)
        dirty = false
        onAccent(color)
    }

    // Applying the accent restyles the whole app and re-blurs the sheet over it, which is too heavy to
    // do every frame. While a slider is dragged only the picker itself (sliders, tracks, preview) is live;
    // the app takes the colour on release. Keyboard / accessibility steps apply after a short pause.
    val live = current()
    val latestOnAccent by rememberUpdatedState(onAccent)
    fun commit() {
        if (dirty) {
            dirty = false
            latestOnAccent(current())
        }
    }
    LaunchedEffect(hue, saturation, shade) {
        if (dirty) {
            delay(400)
            commit()
        }
    }

    Column {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Presets.forEach { preset -> Swatch(preset, selected = preset.color == accent) { select(preset.color) } }
        }

        Spacer(Modifier.height(16.dp))
        SliderRow("Hue") {
            GlassSlider(
                value = hue,
                onValueChange = { hue = it; dirty = true },
                onValueChangeFinished = ::commit,
                trackBrush = Brush.horizontalGradient(List(7) { Color.hsv(it * 60f, 1f, 1f) }),
            )
        }
        SliderRow("Saturation") {
            GlassSlider(
                value = saturation,
                onValueChange = { saturation = it; dirty = true },
                onValueChangeFinished = ::commit,
                trackBrush = Brush.horizontalGradient(
                    listOf(Color.hsv(hue * 360f, 0f, brightness()), Color.hsv(hue * 360f, 1f, brightness())),
                ),
            )
        }
        SliderRow("Brightness") {
            GlassSlider(
                value = shade,
                onValueChange = { shade = it; dirty = true },
                onValueChangeFinished = ::commit,
                trackBrush = Brush.horizontalGradient(
                    listOf(
                        Color.hsv(hue * 360f, saturation, MIN_BRIGHTNESS),
                        Color.hsv(hue * 360f, saturation, 1f),
                    ),
                ),
            )
        }

        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(28.dp).clip(CircleShape).background(live).border(1.dp, Prism.colors.outline, CircleShape))
            Spacer(Modifier.size(12.dp))
            PrismText(
                text = "#%06X".format(live.toArgb() and 0xFFFFFF),
                fontSize = 15.sp,
                color = Prism.subText,
                modifier = Modifier.weight(1f),
            )
            GlassButton(
                backdrop,
                "Reset",
                onClick = { select(DefaultAccent) },
                size = ButtonSize.Small,
                enabled = accent != DefaultAccent,
            )
        }
    }
}

@Composable
private fun SliderRow(label: String, slider: @Composable () -> Unit) {
    PrismText(label, fontSize = 14.sp, color = Prism.subText, fontWeight = FontWeight.Medium)
    slider()
    Spacer(Modifier.height(4.dp))
}

@Composable
private fun Swatch(preset: Preset, selected: Boolean, onClick: () -> Unit) {
    val noRipple = remember { MutableInteractionSource() }
    val motion = LocalMotion.current
    val scale by animateFloatAsState(
        targetValue = if (selected) 1f + 0.08f * motion.magnitude else 1f,
        animationSpec = motion.glide(),
        label = "swatchScale",
    )
    Box(
        Modifier
            .size(34.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(CircleShape)
            .background(preset.color)
            .then(if (selected) Modifier.border(3.dp, Prism.text, CircleShape) else Modifier)
            .semantics {
                role = Role.RadioButton
                contentDescription = preset.name
            }
            .clickable(interactionSource = noRipple, indication = null, onClick = onClick),
    )
}
