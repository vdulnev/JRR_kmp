package com.example.jrr.ui.component

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.jrr.ui.theme.Gold

@Composable
fun ObsidianPrimaryButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable () -> Unit
) {
    Button(
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        colors = ButtonDefaults.buttonColors(
            containerColor = Gold,
            contentColor = Color.Black,
            disabledContainerColor = Gold.copy(alpha = 0.5f),
            disabledContentColor = Color.Black.copy(alpha = 0.5f)
        ),
        shape = MaterialTheme.shapes.small,
        contentPadding = PaddingValues(horizontal = 24.dp, vertical = 12.dp),
        content = {
            ProvideTextStyle(value = MaterialTheme.typography.labelLarge) {
                content()
            }
        }
    )
}

@Composable
fun ObsidianSecondaryButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable () -> Unit
) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        colors = ButtonDefaults.outlinedButtonColors(
            contentColor = Gold,
            disabledContentColor = Gold.copy(alpha = 0.5f)
        ),
        border = ButtonDefaults.outlinedButtonBorder(enabled = enabled).copy(
            brush = androidx.compose.ui.graphics.SolidColor(if (enabled) Gold else Gold.copy(alpha = 0.5f))
        ),
        shape = MaterialTheme.shapes.small,
        contentPadding = PaddingValues(horizontal = 24.dp, vertical = 12.dp),
        content = {
            ProvideTextStyle(value = MaterialTheme.typography.labelLarge) {
                content()
            }
        }
    )
}
