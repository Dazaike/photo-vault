package com.dazaike.photovault.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import com.dazaike.photovault.ui.theme.Prism
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop

/**
 * The flat page layer (only [Prism.background]) that every glass control sitting
 * directly on a screen refracts. Never records glass or content, so controls
 * never refract each other. Provided once by [PrismPageHost].
 */
val LocalPageBackdrop = staticCompositionLocalOf<Backdrop> {
    error("LocalPageBackdrop not provided; wrap in PrismPageHost")
}

/** Page root: paints the page colour, records it as the flat backdrop, then draws [content] above it. */
@Composable
fun PrismPageHost(content: @Composable () -> Unit) {
    val backdrop = rememberLayerBackdrop()
    val background = Prism.background
    Box(Modifier.fillMaxSize().background(background)) {
        Box(Modifier.fillMaxSize().layerBackdrop(backdrop).background(background))
        CompositionLocalProvider(LocalPageBackdrop provides backdrop, content = content)
    }
}
