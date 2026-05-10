package com.example.jrr.ui.setup

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.jrr.ui.component.ObsidianPrimaryButton
import com.example.jrr.ui.component.TechnicalLabel

@Composable
fun SetupScreen(
    viewModel: SetupViewModel,
    onSuccess: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()

    if (uiState.isSuccess) {
        onSuccess()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        TechnicalLabel(text = "Server Setup")
        Spacer(modifier = Modifier.height(32.dp))

        OutlinedTextField(
            value = uiState.accessKey,
            onValueChange = { viewModel.onAccessKeyChange(it) },
            label = { Text("JRiver Access Key") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )

        Spacer(modifier = Modifier.height(16.dp))
        Text(text = "OR", style = MaterialTheme.typography.labelMedium)
        Spacer(modifier = Modifier.height(16.dp))

        Row(modifier = Modifier.fillMaxWidth()) {
            OutlinedTextField(
                value = uiState.host,
                onValueChange = { viewModel.onHostChange(it) },
                label = { Text("Host") },
                modifier = Modifier.weight(1f),
                singleLine = true
            )
            Spacer(modifier = Modifier.width(8.dp))
            OutlinedTextField(
                value = uiState.port,
                onValueChange = { viewModel.onPortChange(it) },
                label = { Text("Port") },
                modifier = Modifier.width(100.dp),
                singleLine = true
            )
        }

        Spacer(modifier = Modifier.height(32.dp))
        TechnicalLabel(text = "Authentication")
        Spacer(modifier = Modifier.height(16.dp))

        OutlinedTextField(
            value = uiState.username,
            onValueChange = { viewModel.onUsernameChange(it) },
            label = { Text("Username") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )

        Spacer(modifier = Modifier.height(8.dp))

        OutlinedTextField(
            value = uiState.password,
            onValueChange = { viewModel.onPasswordChange(it) },
            label = { Text("Password") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )

        Spacer(modifier = Modifier.height(32.dp))

        if (uiState.error != null) {
            Text(
                text = uiState.error!!,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(bottom = 16.dp)
            )
        }

        ObsidianPrimaryButton(
            onClick = { viewModel.onConnect() },
            modifier = Modifier.fillMaxWidth(),
            enabled = !uiState.isLoading
        ) {
            if (uiState.isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(24.dp),
                    color = MaterialTheme.colorScheme.onPrimary,
                    strokeWidth = 2.dp
                )
            } else {
                Text("Connect")
            }
        }
    }
}
