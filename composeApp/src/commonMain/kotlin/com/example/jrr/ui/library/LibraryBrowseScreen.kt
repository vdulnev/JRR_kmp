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
            title = when (val state = uiState) {
                is LibraryState.Browsing -> state.path.lastOrNull()?.name ?: "Library"
                is LibraryState.Files -> state.path.lastOrNull()?.name ?: "Tracks"
                is LibraryState.SearchResults -> "Search: ${state.query}"
                else -> "Library"
            },
            showBack = uiState !is LibraryState.Browsing || (uiState as LibraryState.Browsing).path.isNotEmpty(),
            onBack = { viewModel.navigateBack() }
        )

        when (val state = uiState) {
            is LibraryState.Loading -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                }
            }
            is LibraryState.Browsing -> {
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(state.items) { item ->
                        ListItem(
                            headlineContent = { Text(item.name) },
                            trailingContent = { Icon(Icons.Default.ChevronRight, contentDescription = null) },
                            modifier = Modifier.clickable { viewModel.browse(item.id, item.name) }
                        )
                        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant)
                    }
                }
            }
            is LibraryState.Files -> {
                TrackList(tracks = state.tracks)
            }
            is LibraryState.SearchResults -> {
                TrackList(tracks = state.tracks)
            }
            is LibraryState.Error -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(state.message, color = MaterialTheme.colorScheme.error)
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
fun TrackList(tracks: List<com.example.jrr.domain.model.Track>) {
    LazyColumn(modifier = Modifier.fillMaxSize()) {
        items(tracks) { track ->
            ListItem(
                headlineContent = { Text(track.name) },
                supportingContent = { Text("${track.artist} — ${track.album}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) },
                trailingContent = { 
                    TechnicalLabel(text = track.fileType, color = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f))
                }
            )
            HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant)
        }
    }
}
