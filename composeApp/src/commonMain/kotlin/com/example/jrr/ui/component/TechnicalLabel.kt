package com.example.jrr.ui.component

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.example.jrr.ui.theme.OnSurfaceVariant

@Composable
fun TechnicalLabel(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = OnSurfaceVariant
) {
    Text(
        text = text.uppercase(),
        modifier = modifier,
        style = MaterialTheme.typography.labelLarge,
        color = color
    )
}
