package com.dazaike.photovault.ui

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import com.dazaike.photovault.ui.theme.Prism
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.IntSize
import java.io.File

@Composable
fun ZoomableVaultImage(
    file: File,
    maxDimensionPx: Int,
    viewModel: VaultViewModel,
    modifier: Modifier = Modifier,
    contentDescription: String? = null,
    onTap: () -> Unit = {},
    onZoomChanged: (Boolean) -> Unit = {},
) {
    val bitmap by produceState<Bitmap?>(initialValue = null, key1 = file.path) {
        value = viewModel.decryptForDisplay(file, maxDimensionPx)
    }

    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    var containerSize by remember { mutableStateOf(IntSize.Zero) }

    fun updateScale(newScale: Float, newOffset: Offset) {
        val clampedScale = newScale.coerceIn(1f, 5f)
        scale = clampedScale
        if (clampedScale == 1f) {
            offset = Offset.Zero
            onZoomChanged(false)
        } else {
            val maxOffsetX = (containerSize.width * (clampedScale - 1f)) / 2f
            val maxOffsetY = (containerSize.height * (clampedScale - 1f)) / 2f
            offset = Offset(
                x = newOffset.x.coerceIn(-maxOffsetX, maxOffsetX),
                y = newOffset.y.coerceIn(-maxOffsetY, maxOffsetY),
            )
            onZoomChanged(true)
        }
    }

    val current = bitmap
    if (current != null) {
        Box(
            modifier = modifier
                .fillMaxSize()
                .clip(RoundedCornerShape(20.dp))
                .onSizeChanged { containerSize = it }
                .pointerInput(Unit) {
                    detectTapGestures(
                        onTap = { onTap() },
                        onDoubleTap = { tapOffset ->
                            if (scale > 1f) {
                                updateScale(1f, Offset.Zero)
                            } else {
                                val targetScale = 2.5f
                                val center = Offset(containerSize.width / 2f, containerSize.height / 2f)
                                val targetOffset = (center - tapOffset) * (targetScale - 1f)
                                updateScale(targetScale, targetOffset)
                            }
                        },
                    )
                }
                .pointerInput(Unit) {
                    detectTransformGestures { _, pan, zoom, _ ->
                        val newScale = scale * zoom
                        val newOffset = offset + pan
                        updateScale(newScale, newOffset)
                    }
                },
            contentAlignment = Alignment.Center,
        ) {
            Image(
                bitmap = current.asImageBitmap(),
                contentDescription = contentDescription,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer(
                        scaleX = scale,
                        scaleY = scale,
                        translationX = offset.x,
                        translationY = offset.y,
                    ),
                contentScale = ContentScale.Fit,
            )
        }
    } else {
        Box(
            modifier = modifier
                .fillMaxSize()
                .clip(RoundedCornerShape(20.dp))
                .background(Prism.colors.fillWeak),
        )
    }
}
