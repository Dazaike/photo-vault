package com.dazaike.photovault.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.EaseInOut
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.progressSemantics
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kyant.shapes.Capsule
import com.kyant.shapes.RoundedRectangle
import com.dazaike.photovault.ui.theme.EaseStandard
import com.dazaike.photovault.ui.theme.LocalContentColor
import com.dazaike.photovault.ui.theme.LocalMotion
import com.dazaike.photovault.ui.theme.Prism
import com.dazaike.photovault.ui.theme.PrismText
import com.dazaike.photovault.ui.theme.accentAlpha
import kotlinx.coroutines.delay

/** Circular activity indicator. Keeps rotating under reduced motion because it carries meaning. */
@Composable
fun Spinner(
    modifier: Modifier = Modifier,
    size: Dp = 20.dp,
    color: Color = LocalContentColor.current,
    strokeWidth: Dp = 2.dp,
) {
    val motion = LocalMotion.current
    val angle by rememberInfiniteTransition(label = "spinner")
        .animateFloat(0f, 360f, infiniteRepeatable(motion.loop(1000)), label = "spinnerAngle")
    Canvas(modifier.graphicsLayer { }.size(size).progressSemantics()) {
        val sw = strokeWidth.toPx()
        val inset = sw / 2f
        drawCircle(
            color.copy(alpha = color.alpha * 0.18f),
            radius = this.size.minDimension / 2f - inset,
            style = Stroke(sw),
        )
        rotate(angle) {
            drawArc(
                color = color,
                startAngle = -90f,
                sweepAngle = 100f,
                useCenter = false,
                topLeft = Offset(inset, inset),
                size = Size(this.size.width - sw, this.size.height - sw),
                style = Stroke(sw, cap = StrokeCap.Round),
            )
        }
    }
}

/** Placeholder block with a soft shimmer sweep (no sweep under reduced motion). */
@Composable
fun SkeletonBlock(modifier: Modifier, shape: Shape = RoundedRectangle(10.dp)) {
    val colors = Prism.colors
    val motion = LocalMotion.current
    val sweep = if (motion.reduced) null else {
        rememberInfiniteTransition(label = "shimmer")
            .animateFloat(0f, 1f, infiniteRepeatable(motion.loop(1400)), label = "shimmerSweep")
    }
    Box(
        modifier
            .graphicsLayer { }
            .clip(shape)
            .background(colors.skeleton)
            .drawWithCache {
                val band = size.width * 0.6f
                val c = colors.ink.copy(alpha = 0.06f * colors.brightness)
                val brush = Brush.horizontalGradient(listOf(Color.Transparent, c, Color.Transparent), 0f, band)
                onDrawBehind {
                    val s = sweep ?: return@onDrawBehind
                    translate(left = -band + (size.width + band) * s.value) {
                        drawRect(brush, size = Size(band, size.height))
                    }
                }
            },
    )
}

/** Linear progress. `null` = indeterminate. */
@Composable
fun GlassProgressBar(progress: Float?, modifier: Modifier = Modifier) {
    val colors = Prism.colors
    val motion = LocalMotion.current
    val fill = Prism.accent.copy(alpha = colors.accentAlpha(0.85f))
    val base = modifier.graphicsLayer { }.fillMaxWidth().height(6.dp).clip(Capsule()).background(colors.track)
    if (progress != null) {
        val p by animateFloatAsState(progress.coerceIn(0f, 1f), motion.glide(), label = "progress")
        Box(
            base
                .progressSemantics(progress.coerceIn(0f, 1f))
                .drawBehind {
                    drawRoundRect(fill, size = Size(size.width * p, size.height), cornerRadius = CornerRadius(size.height / 2f))
                },
        )
    } else if (motion.reduced) {
        val a by rememberInfiniteTransition(label = "pulse").animateFloat(
            0.35f,
            0.75f,
            infiniteRepeatable(motion.loop(1200, EaseStandard), RepeatMode.Reverse),
            label = "pulseAlpha",
        )
        Box(base.progressSemantics().drawBehind { drawRect(fill.copy(alpha = fill.alpha * a)) })
    } else {
        val t by rememberInfiniteTransition(label = "indeterminate")
            .animateFloat(0f, 1f, infiniteRepeatable(motion.loop(1300, EaseInOut)), label = "indeterminateX")
        Box(
            base
                .progressSemantics()
                .drawBehind {
                    val w = size.width * 0.3f
                    drawRoundRect(
                        fill,
                        topLeft = Offset((-0.3f + 1.3f * t) * size.width, 0f),
                        size = Size(w, size.height),
                        cornerRadius = CornerRadius(size.height / 2f),
                    )
                },
        )
    }
}

/** Section-level loading state; fades in after 150 ms so quick loads never flash. */
@Composable
fun SectionLoader(message: String?, modifier: Modifier = Modifier) {
    val colors = Prism.colors
    val motion = LocalMotion.current
    val alpha = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        delay(150)
        alpha.animateTo(1f, motion.fade(200))
    }
    Box(modifier.graphicsLayer { this.alpha = alpha.value }, contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Spinner(size = 28.dp, color = colors.subText)
            if (message != null) {
                Spacer(Modifier.height(12.dp))
                PrismText(message, fontSize = 14.sp, color = colors.subText)
            }
        }
    }
}
