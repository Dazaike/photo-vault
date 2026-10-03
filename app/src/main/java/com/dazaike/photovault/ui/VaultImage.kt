package com.dazaike.photovault.ui

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import com.dazaike.photovault.ui.theme.Prism
import com.kyant.shapes.RoundedRectangle
import java.io.File

private val TileShape = RoundedRectangle(14.dp)

/**
 * Decrypts and shows [file]. Thumbnail-sized requests are served synchronously from [ThumbCache]
 * when present (no flash on scroll-back); otherwise decoded off-thread with bounded parallelism,
 * and cancelled automatically when the tile leaves composition.
 */
@Composable
fun VaultImage(
    file: File,
    maxDimensionPx: Int,
    viewModel: VaultViewModel,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop,
    contentDescription: String? = null,
) {
    val bitmap by produceState<Bitmap?>(
        initialValue = viewModel.cachedThumb(file, maxDimensionPx),
        key1 = file.path,
        key2 = maxDimensionPx,
    ) {
        if (value == null) value = viewModel.decryptForDisplay(file, maxDimensionPx)
    }
    val current = bitmap
    if (current != null) {
        val image = remember(current) { current.asImageBitmap() }
        Image(
            bitmap = image,
            contentDescription = contentDescription,
            modifier = modifier.clip(TileShape),
            contentScale = contentScale,
        )
    } else {
        Box(modifier.clip(TileShape).background(Prism.colors.fillWeak))
    }
}
