package com.example.jrr.ui.player

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Link
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.jrr.ui.component.TechnicalLabel

@Composable
fun ZoneControllerScreen(
    viewModel: PlayerViewModel
) {
    val uiState by viewModel.uiState.collectAsState()
    val activeZoneId = uiState.activeZoneId

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        TechnicalLabel(text = "Active Zones")
        Spacer(modifier = Modifier.height(16.dp))

        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(uiState.zones) { zone ->
                val isActive = zone.id == activeZoneId

                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = if (isActive) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f) else MaterialTheme.colorScheme.surface,
                    shape = MaterialTheme.shapes.small,
                    onClick = { viewModel.selectZone(zone.id) }
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = zone.name,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = if (isActive) FontWeight.Bold else FontWeight.Normal
                            )
                            if (zone.isDLNA) {
                                Text(
                                    "DLNA Renderer",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        if (isActive) {
                            Icon(
                                Icons.Default.Link,
                                contentDescription = "Active",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }
        }
    }
}
