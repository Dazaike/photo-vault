package com.dazaike.photovault.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.KeyboardActionHandler
import androidx.compose.foundation.text.input.TextFieldDecorator
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.clearText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kyant.backdrop.Backdrop
import com.kyant.shapes.Capsule
import com.dazaike.photovault.ui.theme.LocalMotion
import com.dazaike.photovault.ui.theme.Outfit
import com.dazaike.photovault.ui.theme.Prism
import com.dazaike.photovault.ui.theme.PrismText

/**
 * Glass search capsule. Focus lights an accent rim and tints the search icon; the clear
 * button empties the query without taking focus from the field.
 */
@Composable
fun GlassSearchBar(
    backdrop: Backdrop,
    state: TextFieldState,
    modifier: Modifier = Modifier,
    placeholder: String = "Search",
    onSearch: (String) -> Unit = {},
) {
    val colors = Prism.colors
    val accent = Prism.accent
    val motion = LocalMotion.current
    val m = motion.magnitude
    val interactionSource = remember { MutableInteractionSource() }
    val focused by interactionSource.collectIsFocusedAsState()
    val focusManager = LocalFocusManager.current
    val focus = animateFloatAsState(if (focused) 1f else 0f, motion.fade(200), label = "searchFocus")
    val f = focus.value
    val hasText = state.text.isNotEmpty()

    Row(
        modifier
            .height(48.dp)
            .liquidGlass(
                backdrop = backdrop,
                shape = { Capsule() },
                depth = glassDepth(elevation = 4.dp, lift = { focus.value }),
                blurRadius = 12.dp,
                surface = colors.field.copy(alpha = 0.6f),
                layerBlock = {
                    val s = 1f + 0.025f * m * focus.value
                    scaleX = s
                    scaleY = s
                },
                onDrawFront = {
                    val p = focus.value
                    if (p > 0f) {
                        val outline = Capsule().createOutline(size, layoutDirection, this)
                        drawOutline(outline, colors.ink.copy(alpha = 0.06f * p * colors.brightness))
                        val w = 1.5.dp.toPx()
                        val rim = Capsule().createOutline(
                            Size(size.width - w, size.height - w),
                            layoutDirection,
                            this,
                        )
                        translate(w / 2f, w / 2f) {
                            drawOutline(rim, accent.copy(alpha = 0.9f * p), style = Stroke(w))
                        }
                    }
                },
            )
            .padding(start = 14.dp, end = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PrismIcon(PrismIcons.Search, null, size = 20.dp, tint = lerp(colors.subText, accent, f))
        Spacer(Modifier.width(10.dp))
        BasicTextField(
            state = state,
            modifier = Modifier
                .weight(1f)
                .escapeClearsFocus(focusManager) { focused },
            lineLimits = TextFieldLineLimits.SingleLine,
            textStyle = TextStyle(fontFamily = Outfit, fontSize = 16.sp, color = colors.text),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            onKeyboardAction = KeyboardActionHandler { onSearch(state.text.toString()) },
            interactionSource = interactionSource,
            cursorBrush = SolidColor(accent),
            decorator = TextFieldDecorator { inner ->
                Box(contentAlignment = Alignment.CenterStart) {
                    if (state.text.isEmpty()) {
                        PrismText(
                            placeholder,
                            color = colors.subText,
                            fontSize = 16.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    inner()
                }
            },
        )
        AnimatedVisibility(
            visible = hasText,
            enter = fadeIn(motion.fade(150)) + scaleIn(motion.fade(150), initialScale = 1f - 0.2f * m),
            exit = fadeOut(motion.fade(150)) + scaleOut(motion.fade(150), targetScale = 1f - 0.2f * m),
            label = "searchClear",
        ) {
            GlassIconButton(
                backdrop = backdrop,
                icon = PrismIcons.Close,
                contentDescription = "Clear search",
                onClick = { state.clearText() },
                variant = ButtonVariant.Ghost,
                size = 32.dp,
            )
        }
    }
}
