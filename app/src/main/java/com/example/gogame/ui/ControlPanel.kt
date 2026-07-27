package com.example.gogame.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ControlPanel(
    onPass: () -> Unit,
    onUndo: () -> Unit,
    onNewGame: () -> Unit,
    onResign: () -> Unit,
    onModeChange: (GameMode) -> Unit,
    currentMode: GameMode,
    canUndo: Boolean,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(8.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = currentMode == GameMode.PVP,
                    onClick = { onModeChange(GameMode.PVP) },
                    label = { Text("双人对战") },
                    modifier = Modifier.weight(1f)
                )
                FilterChip(
                    selected = currentMode == GameMode.PVE,
                    onClick = { onModeChange(GameMode.PVE) },
                    label = { Text("人机对战") },
                    modifier = Modifier.weight(1f)
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = onPass,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("虚手")
                }
                OutlinedButton(
                    onClick = onUndo,
                    enabled = canUndo,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("悔棋")
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = onNewGame,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("新局")
                }
                OutlinedButton(
                    onClick = onResign,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Text("认输")
                }
            }
        }
    }
}
