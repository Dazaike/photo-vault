package com.dazaike.photovault.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kyant.shapes.RoundedRectangle
import com.dazaike.photovault.ui.theme.LocalMotion
import com.dazaike.photovault.ui.theme.Prism
import com.dazaike.photovault.ui.theme.PrismText
import com.dazaike.photovault.ui.theme.accentAlpha

/**
 * Flat (non-glass) checkbox; the whole row toggles. The check mark is the `ic_check`
 * geometry, drawn as a path trimmed to the animated fraction.
 */
@Composable
fun GlassCheckbox(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    label: String? = null,
    enabled: Boolean = true,
) {
    val colors = Prism.colors
    val accent = Prism.accent
    val onAccent = Prism.onAccent
    val motion = LocalMotion.current
    val haptics = LocalHaptics.current
    val press = rememberPressState(enabled)
    val shape = RoundedRectangle(7.dp)

    val fill = animateColorAsState(
        if (checked) accent.copy(alpha = colors.accentAlpha(0.9f)) else colors.fillWeak,
        motion.fade(180),
        label = "checkboxFill",
    )
    val border = animateColorAsState(
        if (checked) colors.outline.copy(alpha = 0f) else colors.outline,
        motion.fade(180),
        label = "checkboxBorder",
    )
    val draw = animateFloatAsState(if (checked) 1f else 0f, motion.glide(), label = "checkDraw")

    Row(
        modifier
            .heightIn(min = 48.dp)
            .alpha(if (enabled) 1f else 0.4f)
            .pressInput(press, enabled)
            .toggleable(
                value = checked,
                interactionSource = press.interactionSource,
                indication = null,
                enabled = enabled,
                role = Role.Checkbox,
            ) { new ->
                haptics.perform(if (new) HapticKind.ToggleOn else HapticKind.ToggleOff)
                onCheckedChange(new)
            },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .focusRing(press, shape, accent.copy(alpha = 0.85f))
                .graphicsLayer {
                    val s = 1f - 0.06f * motion.magnitude * press.progress
                    scaleX = s
                    scaleY = s
                }
                .size(22.dp)
                .drawWithCache {
                    val outline = shape.createOutline(size, layoutDirection, this)
                    val borderPx = 1.5.dp.toPx()
                    val borderOutline = shape.createOutline(
                        Size(size.width - borderPx, size.height - borderPx),
                        layoutDirection,
                        this,
                    )
                    // ic_check geometry (24 viewport) scaled to the 22dp box.
                    val k = size.width / 24f
                    val full = Path().apply {
                        moveTo(5f * k, 12.5f * k)
                        lineTo(9.5f * k, 17f * k)
                        lineTo(19f * k, 7f * k)
                    }
                    val measure = PathMeasure().apply { setPath(full, false) }
                    val length = measure.length
                    val segment = Path()
                    val checkStroke = Stroke(
                        width = 2.dp.toPx(),
                        cap = StrokeCap.Round,
                        join = StrokeJoin.Round,
                    )
                    onDrawBehind {
                        drawOutline(outline, fill.value)
                        val b = border.value
                        if (b.alpha > 0f) {
                            translate(borderPx / 2f, borderPx / 2f) {
                                drawOutline(borderOutline, b, style = Stroke(borderPx))
                            }
                        }
                        val d = draw.value
                        if (d > 0f) {
                            segment.reset()
                            measure.getSegment(0f, length * d, segment, true)
                            drawPath(segment, onAccent, style = checkStroke)
                        }
                    }
                },
        )
        if (label != null) {
            Spacer(Modifier.width(12.dp))
            PrismText(label, fontSize = 16.sp)
        }
    }
}

/** Flat radio button; the whole row selects. Wrap groups in `Modifier.selectableGroup()`. */
@Composable
fun GlassRadio(
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    label: String? = null,
    enabled: Boolean = true,
) {
    val colors = Prism.colors
    val accent = Prism.accent
    val motion = LocalMotion.current
    val haptics = LocalHaptics.current
    val press = rememberPressState(enabled)

    val border = animateColorAsState(
        if (selected) accent else colors.outline,
        motion.fade(180),
        label = "radioBorder",
    )
    val dot = animateFloatAsState(if (selected) 1f else 0f, motion.glide(), label = "radioDot")

    Row(
        modifier
            .heightIn(min = 48.dp)
            .alpha(if (enabled) 1f else 0.4f)
            .pressInput(press, enabled)
            .selectable(
                selected = selected,
                interactionSource = press.interactionSource,
                indication = null,
                enabled = enabled,
                role = Role.RadioButton,
            ) {
                if (!selected) haptics.perform(HapticKind.Tick)
                onClick()
            },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .focusRing(press, CircleShape, accent.copy(alpha = 0.85f))
                .graphicsLayer {
                    val s = 1f - 0.06f * motion.magnitude * press.progress
                    scaleX = s
                    scaleY = s
                }
                .size(22.dp)
                .drawBehind {
                    val borderPx = 1.5.dp.toPx()
                    val c = Offset(size.width / 2f, size.height / 2f)
                    drawCircle(
                        border.value,
                        radius = (size.minDimension - borderPx) / 2f,
                        center = c,
                        style = Stroke(borderPx),
                    )
                    val d = dot.value
                    if (d > 0f) drawCircle(accent, radius = 5.dp.toPx() * d, center = c)
                },
        )
        if (label != null) {
            Spacer(Modifier.width(12.dp))
            PrismText(label, fontSize = 16.sp)
        }
    }
}
