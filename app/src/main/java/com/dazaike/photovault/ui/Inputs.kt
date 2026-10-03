package com.dazaike.photovault.ui

import androidx.annotation.DrawableRes
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicSecureTextField
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.InputTransformation
import androidx.compose.foundation.text.input.KeyboardActionHandler
import androidx.compose.foundation.text.input.TextFieldDecorator
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.TextObfuscationMode
import androidx.compose.foundation.text.input.maxLength
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusManager
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.error
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.shapes.RoundedRectangle
import com.dazaike.photovault.ui.theme.LocalMotion
import com.dazaike.photovault.ui.theme.Outfit
import com.dazaike.photovault.ui.theme.Prism
import com.dazaike.photovault.ui.theme.PrismText

/**
 * Flat text field with a floating label and a reserved supporting line, so validation
 * messages never shift layout. [modifier] is applied to the text field itself, so
 * `Modifier.onFocusChanged` on it observes the field's own focus.
 */
@Composable
fun GlassTextField(
    state: TextFieldState,
    label: String,
    modifier: Modifier = Modifier,
    placeholder: String = "",
    supportingText: String? = null,
    error: String? = null,
    enabled: Boolean = true,
    singleLine: Boolean = true,
    maxLength: Int? = null,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    onSubmit: (() -> Unit)? = null,
    @DrawableRes leadingIcon: Int? = null,
) {
    val colors = Prism.colors
    val accent = Prism.accent
    val interactionSource = remember { MutableInteractionSource() }
    val focused by interactionSource.collectIsFocusedAsState()
    val focusManager = LocalFocusManager.current

    BasicTextField(
        state = state,
        modifier = modifier
            .alpha(if (enabled) 1f else 0.5f)
            .escapeClearsFocus(focusManager) { focused }
            .fieldErrorSemantics(error),
        enabled = enabled,
        inputTransformation = maxLength?.let { InputTransformation.maxLength(it) },
        textStyle = TextStyle(fontFamily = Outfit, fontSize = 16.sp, color = colors.text),
        keyboardOptions = keyboardOptions,
        onKeyboardAction = submitHandler(onSubmit),
        lineLimits = if (singleLine) {
            TextFieldLineLimits.SingleLine
        } else {
            TextFieldLineLimits.MultiLine(minHeightInLines = 3, maxHeightInLines = 6)
        },
        interactionSource = interactionSource,
        cursorBrush = SolidColor(accent),
        decorator = TextFieldDecorator { inner ->
            FieldDecoration(
                state = state,
                inner = inner,
                label = label,
                placeholder = placeholder,
                focused = focused,
                error = error,
                supportingText = supportingText,
                maxLength = maxLength,
                singleLine = singleLine,
                leadingIcon = leadingIcon,
            )
        },
    )
}

/**
 * Password field sharing the [GlassTextField] decoration, with a trailing show/hide toggle.
 * Toggling keeps text and cursor because the [state] object is unchanged.
 */
@Composable
fun GlassPasswordField(
    state: TextFieldState,
    label: String,
    modifier: Modifier = Modifier,
    supportingText: String? = null,
    error: String? = null,
    enabled: Boolean = true,
    onSubmit: (() -> Unit)? = null,
) {
    val colors = Prism.colors
    val accent = Prism.accent
    val interactionSource = remember { MutableInteractionSource() }
    val focused by interactionSource.collectIsFocusedAsState()
    val focusManager = LocalFocusManager.current
    var visible by rememberSaveable { mutableStateOf(false) }
    // The Ghost variant draws no glass and never samples its backdrop; this unrecorded
    // layer only satisfies GlassIconButton's parameter.
    val unusedBackdrop = rememberLayerBackdrop()

    BasicSecureTextField(
        state = state,
        modifier = modifier
            .alpha(if (enabled) 1f else 0.5f)
            .escapeClearsFocus(focusManager) { focused }
            .fieldErrorSemantics(error),
        enabled = enabled,
        textStyle = TextStyle(fontFamily = Outfit, fontSize = 16.sp, color = colors.text),
        onKeyboardAction = submitHandler(onSubmit),
        interactionSource = interactionSource,
        cursorBrush = SolidColor(accent),
        textObfuscationMode = if (visible) TextObfuscationMode.Visible else TextObfuscationMode.RevealLastTyped,
        decorator = TextFieldDecorator { inner ->
            FieldDecoration(
                state = state,
                inner = inner,
                label = label,
                placeholder = "",
                focused = focused,
                error = error,
                supportingText = supportingText,
                maxLength = null,
                singleLine = true,
                leadingIcon = null,
                trailing = {
                    GlassIconButton(
                        backdrop = unusedBackdrop,
                        icon = if (visible) PrismIcons.EyeOff else PrismIcons.Eye,
                        contentDescription = if (visible) "Hide password" else "Show password",
                        onClick = { visible = !visible },
                        variant = ButtonVariant.Ghost,
                        size = 36.dp,
                        enabled = enabled,
                    )
                },
            )
        },
    )
}

/** Runs [onSubmit] instead of the default IME action when provided. */
private fun submitHandler(onSubmit: (() -> Unit)?): KeyboardActionHandler =
    KeyboardActionHandler { performDefault ->
        if (onSubmit != null) onSubmit() else performDefault()
    }

/** Escape (key up) while focused drops focus instead of propagating to Back. */
internal fun Modifier.escapeClearsFocus(focusManager: FocusManager, focused: () -> Boolean): Modifier =
    onPreviewKeyEvent {
        if (it.key == Key.Escape && it.type == KeyEventType.KeyUp && focused()) {
            focusManager.clearFocus()
            true
        } else {
            false
        }
    }

/** Announces the validation message as the field's error. */
private fun Modifier.fieldErrorSemantics(message: String?): Modifier =
    if (message == null) this else semantics { this.error(message) }

private data class SupportLine(val text: String, val isError: Boolean)

@Composable
private fun FieldDecoration(
    state: TextFieldState,
    inner: @Composable () -> Unit,
    label: String,
    placeholder: String,
    focused: Boolean,
    error: String?,
    supportingText: String?,
    maxLength: Int?,
    singleLine: Boolean,
    @DrawableRes leadingIcon: Int?,
    trailing: (@Composable () -> Unit)? = null,
) {
    val colors = Prism.colors
    val accent = Prism.accent
    val motion = LocalMotion.current
    val slidePx = with(LocalDensity.current) { (4.dp * motion.magnitude).roundToPx() }
    val shape = RoundedRectangle(18.dp)
    val isError = error != null
    val empty = state.text.isEmpty()

    val borderColor = animateColorAsState(
        when {
            isError -> colors.error
            focused -> accent.copy(alpha = 0.8f)
            else -> colors.outline
        },
        motion.fade(180),
        label = "fieldBorder",
    )
    val borderWidth = animateDpAsState(
        if (isError || focused) 1.5.dp else 1.dp,
        motion.fade(180),
        label = "fieldBorderWidth",
    )
    val focusFill = animateFloatAsState(if (focused) 1f else 0f, motion.fade(180), label = "fieldFocus")
    val lp = animateFloatAsState(if (focused || !empty) 1f else 0f, motion.glide(), label = "fieldLabel")
    val labelColor by animateColorAsState(
        when {
            isError -> colors.error
            focused -> accent
            else -> colors.subText
        },
        motion.fade(180),
        label = "fieldLabelColor",
    )

    Column(Modifier.fillMaxWidth()) {
        Row(
            Modifier
                .fillMaxWidth()
                .drawBehind {
                    val outline = shape.createOutline(size, layoutDirection, this)
                    drawOutline(outline, colors.field)
                    val f = focusFill.value
                    if (f > 0f) {
                        drawOutline(outline, colors.fillWeak.copy(alpha = colors.fillWeak.alpha * f))
                    }
                    val w = borderWidth.value.toPx()
                    val inset = shape.createOutline(
                        Size(size.width - w, size.height - w),
                        layoutDirection,
                        this,
                    )
                    translate(w / 2f, w / 2f) {
                        drawOutline(inset, borderColor.value, style = Stroke(w))
                    }
                }
                .padding(start = 16.dp, end = if (trailing != null) 8.dp else 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (leadingIcon != null) {
                PrismIcon(leadingIcon, null, size = 20.dp, tint = colors.subText)
                Spacer(Modifier.width(12.dp))
            }
            Box(
                Modifier
                    .weight(1f)
                    .padding(top = 24.dp, bottom = 10.dp),
                contentAlignment = Alignment.TopStart,
            ) {
                PrismText(
                    label,
                    modifier = Modifier.graphicsLayer {
                        val p = lp.value
                        transformOrigin = TransformOrigin(0f, 0f)
                        translationY = -14.dp.toPx() * p
                        val s = 1f - 0.25f * p
                        scaleX = s
                        scaleY = s
                    },
                    color = labelColor,
                    fontSize = 16.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (focused && empty && placeholder.isNotEmpty()) {
                    PrismText(
                        placeholder,
                        color = colors.subText.copy(alpha = colors.subText.alpha * 0.7f),
                        fontSize = 16.sp,
                        maxLines = if (singleLine) 1 else Int.MAX_VALUE,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                inner()
            }
            if (trailing != null) {
                Spacer(Modifier.width(4.dp))
                trailing()
            }
        }
        Row(
            Modifier
                .fillMaxWidth()
                .height(20.dp)
                .padding(start = 16.dp, end = 16.dp, top = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val line = when {
                error != null -> SupportLine(error, isError = true)
                supportingText != null -> SupportLine(supportingText, isError = false)
                else -> null
            }
            AnimatedContent(
                targetState = line,
                modifier = Modifier.weight(1f),
                transitionSpec = {
                    (
                        fadeIn(motion.fade(180)) +
                            slideInVertically(motion.fade(180)) { slidePx }
                        ) togetherWith fadeOut(motion.fade(120))
                },
                contentAlignment = Alignment.CenterStart,
                label = "fieldSupport",
            ) { s ->
                if (s != null) {
                    val tint = if (s.isError) colors.error else colors.subText
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (s.isError) {
                            PrismIcon(PrismIcons.Alert, null, size = 14.dp, tint = tint)
                            Spacer(Modifier.width(4.dp))
                        }
                        PrismText(
                            s.text,
                            color = tint,
                            fontSize = 12.5.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
            if (maxLength != null) {
                Spacer(Modifier.width(8.dp))
                PrismText(
                    "${state.text.length}/$maxLength",
                    color = colors.subText,
                    fontSize = 12.5.sp,
                    maxLines = 1,
                )
            }
        }
    }
}
