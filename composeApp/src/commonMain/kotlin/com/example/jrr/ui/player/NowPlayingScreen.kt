package com.example.jrr.ui.player

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeDown
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.example.jrr.domain.model.PlaybackState
import com.example.jrr.ui.component.ObsidianVolumeSlider
import com.example.jrr.ui.component.TechnicalLabel

@Composable
fun NowPlayingScreen(
    viewModel: PlayerViewModel,
    serverAddress: String // Needed to resolve relative image URLs
) {
    val uiState by viewModel.uiState.collectAsState()
    val status = uiState.status

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Artwork
        val imageUrl = status?.trackInfo?.imageUrl?.let {
            if (it.startsWith("http")) it else "$serverAddress/$it"
        }

        Box(
            modifier = Modifier
                .widthIn(max = 360.dp)
                .fillMaxWidth()
                .aspectRatio(1f)
                .clip(MaterialTheme.shapes.medium)
        ) {
            if (imageUrl != null) {
                AsyncImage(
                    model = imageUrl,
                    contentDescription = "Album Art",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            } else {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.surfaceVariant
                ) {
                    Icon(
                        imageVector = Icons.Default.MusicNote,
                        contentDescription = null,
                        modifier = Modifier.size(64.dp).align(Alignment.Center),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(32.dp))

        // Metadata
        Text(
            text = status?.trackInfo?.name ?: "Not Playing",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            maxLines = 1
        )
        Text(
            text = status?.trackInfo?.artist ?: "",
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1
        )

        Spacer(modifier = Modifier.height(8.dp))

        // Technical Info
        if (status?.trackInfo != null) {
            TechnicalLabel(
                text = "${status.trackInfo.bitrate} kbps | ${status.trackInfo.sampleRate / 1000.0} kHz | ${status.trackInfo.bitDepth} bit",
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f)
            )
        }

        Spacer(modifier = Modifier.weight(1f))

        // Volume Slider
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                Icons.AutoMirrored.Filled.VolumeDown,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.outline
            )
            ObsidianVolumeSlider(
                value = status?.volume ?: 0f,
                onValueChange = { viewModel.setVolume(it) },
                modifier = Modifier.weight(1f).padding(horizontal = 8.dp)
            )
            Icon(
                Icons.AutoMirrored.Filled.VolumeUp,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.outline
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Progress Bar (Simple version)
        val progress = if (status != null && status.durationMs > 0) {
            status.positionMs.toFloat() / status.durationMs.toFloat()
        } else 0f

        LinearProgressIndicator(
            progress = { progress },
            modifier = Modifier.fillMaxWidth().height(4.dp),
            color = MaterialTheme.colorScheme.primary,
            trackColor = MaterialTheme.colorScheme.surfaceVariant
        )

        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(status?.positionDisplay ?: "0:00", style = MaterialTheme.typography.labelMedium)
            Text(
                status?.playingNowPositionDisplay ?: "",
                style = MaterialTheme.typography.labelMedium
            )
        }

        Spacer(modifier = Modifier.height(32.dp))

        // Transport Controls
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = { viewModel.previous() }) {
                Icon(
                    Icons.Default.SkipPrevious,
                    contentDescription = "Previous",
                    modifier = Modifier.size(32.dp)
                )
            }

            IconButton(
                onClick = { viewModel.playPause() },
                modifier = Modifier.size(64.dp),
                colors = IconButtonDefaults.iconButtonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                )
            ) {
                val icon =
                    if (status?.state == PlaybackState.PLAYING) Icons.Default.Pause else Icons.Default.PlayArrow
                Icon(icon, contentDescription = "Play/Pause", modifier = Modifier.size(40.dp))
            }

            IconButton(onClick = { viewModel.next() }) {
                Icon(
                    Icons.Default.SkipNext,
                    contentDescription = "Next",
                    modifier = Modifier.size(32.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(32.dp))

        // Zone Info
        TechnicalLabel(text = "Zone: ${status?.zoneName ?: "Unknown"}")
    }
}
