package com.dazaike.photovault.ui

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.text.font.FontWeight
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.dazaike.photovault.R
import com.dazaike.photovault.data.VaultItemEntity
import com.dazaike.photovault.ui.theme.Prism
import com.dazaike.photovault.ui.theme.PrismText
import java.io.File
import kotlinx.coroutines.delay

/**
 * ExoPlayer-backed playback with a custom Prism transport bar (glass play/pause,
 * scrub slider, elapsed/duration) instead of the legacy Holo-styled
 * VideoView/MediaController overlay. Decrypts [item]'s original video into a
 * plaintext temp file (ExoPlayer's local file source cannot read EncryptedFile
 * directly); the temp file is deleted as soon as this composable leaves
 * composition (page swiped away / viewer closed).
 */
@Composable
fun VaultVideoPlayer(item: VaultItemEntity, viewModel: VaultViewModel, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var tempFile by remember(item.id) { mutableStateOf<File?>(null) }

    LaunchedEffect(item.id) {
        tempFile = viewModel.decryptVideoToTemp(item)
    }

    val file = tempFile
    if (file == null) {
        Box(modifier.background(Prism.colors.fillWeak))
        return
    }

    val exoPlayer = remember(file.path) {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(MediaItem.fromUri(Uri.fromFile(file)))
            prepare()
            playWhenReady = true
        }
    }

    var isPlaying by remember(file.path) { mutableStateOf(true) }
    var positionMs by remember(file.path) { mutableLongStateOf(0L) }
    var durationMs by remember(file.path) { mutableLongStateOf(0L) }
    var isSeeking by remember(file.path) { mutableStateOf(false) }
    var durationReported by remember(file.path) { mutableStateOf(false) }

    DisposableEffect(exoPlayer) {
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(playing: Boolean) {
                isPlaying = playing
            }
        }
        exoPlayer.addListener(listener)
        onDispose {
            exoPlayer.removeListener(listener)
            exoPlayer.release()
            viewModel.deleteTempPlayback(file)
        }
    }

    LaunchedEffect(exoPlayer) {
        while (true) {
            if (!isSeeking) {
                positionMs = exoPlayer.currentPosition.coerceAtLeast(0)
                durationMs = exoPlayer.duration.coerceAtLeast(0)
            }
            delay(300)
        }
    }

    // Persist the exact length once the player knows it (feeds the grid badge).
    LaunchedEffect(durationMs) {
        if (!durationReported && durationMs > 0) {
            durationReported = true
            viewModel.reportDuration(item, durationMs)
        }
    }

    Box(modifier = modifier.background(Color.Black)) {
        AndroidView(
            factory = { ctx ->
                (android.view.LayoutInflater.from(ctx).inflate(R.layout.vault_player_view, null) as PlayerView).apply {
                    player = exoPlayer
                }
            },
            modifier = Modifier.fillMaxSize(),
        )

        val backdrop = LocalPageBackdrop.current
        Row(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            val label = if (isPlaying) "Pause" else "Play"
            TooltipBox(label) {
                GlassIconButton(
                    backdrop,
                    if (isPlaying) PrismIcons.Pause else PrismIcons.Play,
                    label,
                    { if (exoPlayer.isPlaying) exoPlayer.pause() else exoPlayer.play() },
                )
            }
            GlassSlider(
                value = if (durationMs > 0) (positionMs.toFloat() / durationMs).coerceIn(0f, 1f) else 0f,
                onValueChange = { fraction ->
                    isSeeking = true
                    positionMs = (fraction * durationMs).toLong()
                },
                modifier = Modifier.weight(1f),
                onValueChangeFinished = {
                    exoPlayer.seekTo(positionMs)
                    isSeeking = false
                },
                contentDescription = "Seek",
            )
            PrismText(
                "${formatDuration(positionMs)} / ${formatDuration(durationMs)}",
                color = Color.White,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
            )
        }
    }
}
