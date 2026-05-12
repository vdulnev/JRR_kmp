package com.example.jrr.ui.library

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.jrr.ui.component.TechnicalLabel

@Composable
fun LibraryBrowseScreen(
    viewModel: LibraryViewModel
) {
    val uiState by viewModel.uiState.collectAsState()

    Column(modifier = Modifier.fillMaxSize()) {
        LibraryTopBar(
            title = uiState.navigationStack.lastOrNull()?.name ?: "Library",
            showBack = uiState.navigationStack.size > 1,
            onBack = { viewModel.navigateBack() }
        )

        if (uiState.isLoading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
            }
        } else if (uiState.error != null) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(uiState.error!!.message, color = MaterialTheme.colorScheme.error)
            }
        } else {
            Column(modifier = Modifier.fillMaxSize()) {
                if (uiState.searchQuery.isNotBlank() && uiState.searchResults.isNotEmpty()) {
                    TechnicalLabel(text = "Search Results", modifier = Modifier.padding(16.dp))
                    TrackList(tracks = uiState.searchResults, onItemClick = { viewModel.playTrack(it) })
                } else if (uiState.tracks.isNotEmpty()) {
                    TrackList(tracks = uiState.tracks, onItemClick = { viewModel.playTrack(it) })
                } else {
                    LazyColumn(modifier = Modifier.fillMaxSize()) {
                        items(uiState.children) { item ->
                            ListItem(
                                headlineContent = { Text(item.name) },
                                trailingContent = { Icon(Icons.Default.ChevronRight, contentDescription = null) },
                                modifier = Modifier.clickable { viewModel.browse(item.id, item.name) }
                            )
                            HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun LibraryTopBar(
    title: String,
    showBack: Boolean,
    onBack: () -> Unit
) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 4.dp
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().height(64.dp).padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (showBack) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                }
            } else {
                Spacer(modifier = Modifier.width(48.dp))
            }

            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
                maxLines = 1
            )
        }
    }
}

@Composable
fun TrackList(
    tracks: List<com.example.jrr.domain.model.Track>,
    onItemClick: (com.example.jrr.domain.model.Track) -> Unit
) {
    LazyColumn(modifier = Modifier.fillMaxSize()) {
        items(tracks) { track ->
            ListItem(
                headlineContent = { Text(track.name) },
                supportingContent = { Text("${track.artist} — ${track.album}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) },
                trailingContent = { 
                    TechnicalLabel(text = track.fileType, color = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f))
                },
                modifier = Modifier.clickable { onItemClick(track) }
            )
            HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant)
        }
    }
}
