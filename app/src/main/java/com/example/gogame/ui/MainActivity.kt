package com.example.gogame.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    GoGameScreen(
                        viewModel = viewModel(
                            factory = GameViewModel.Factory(applicationContext)
                        )
                    )
                }
            }
        }
    }
}

@Composable
fun GoGameScreen(
    viewModel: GameViewModel = viewModel(
        factory = GameViewModel.Factory(
            androidx.compose.ui.platform.LocalContext.current.applicationContext
        )
    )
) {
    val uiState by viewModel.uiState.collectAsState()

    LaunchedEffect(Unit) {
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        GameInfoPanel(
            currentPlayer = uiState.currentPlayer,
            blackCaptures = uiState.blackCaptures,
            whiteCaptures = uiState.whiteCaptures,
            moveCount = uiState.moveCount,
            isGameOver = uiState.isGameOver,
            winner = uiState.winner,
            isAiThinking = uiState.isAiThinking,
            gameMode = uiState.gameMode,
            modifier = Modifier.fillMaxWidth()
        )

        if (uiState.engineError != null) {
            Card(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer
                )
            ) {
                Text(
                    text = "引擎错误: ${uiState.engineError}",
                    modifier = Modifier.padding(12.dp),
                    color = MaterialTheme.colorScheme.onErrorContainer
                )
            }
        }

        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentAlignment = Alignment.Center
        ) {
            GoBoard(
                boardState = viewModel.game.getBoard(),
                lastMove = uiState.lastMove,
                boardSize = uiState.boardSize,
                onCellClick = { x, y ->
                    viewModel.placeStone(x, y)
                },
                modifier = Modifier.fillMaxSize()
            )

            if (uiState.isAiThinking) {
                Card(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(12.dp)
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp),
                            strokeWidth = 2.dp
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("AI 思考中...")
                    }
                }
            }
        }

        ControlPanel(
            onPass = { viewModel.pass() },
            onUndo = { viewModel.undo() },
            onNewGame = { viewModel.newGame() },
            onResign = { viewModel.resign() },
            onModeChange = { mode ->
                viewModel.setGameMode(mode)
            },
            currentMode = uiState.gameMode,
            canUndo = uiState.moveCount > 0 && !uiState.isAiThinking,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Preview(showBackground = true)
@Composable
fun PreviewGoGameScreen() {
    MaterialTheme {
    }
}
