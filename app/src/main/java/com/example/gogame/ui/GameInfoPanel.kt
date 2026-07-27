package com.example.gogame.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.gogame.model.Stone

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GameInfoPanel(
    currentPlayer: Stone,
    blackCaptures: Int,
    whiteCaptures: Int,
    moveCount: Int,
    isGameOver: Boolean,
    winner: Stone?,
    isAiThinking: Boolean,
    gameMode: GameMode,
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
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "模式: ${if (gameMode == GameMode.PVP) "双人对战" else "人机对战"}",
                    fontSize = 14.sp
                )
                Text(
                    text = "第 $moveCount 手",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            Divider()

            if (isGameOver) {
                Text(
                    text = when (winner) {
                        Stone.BLACK -> "黑方胜！"
                        Stone.WHITE -> "白方胜！"
                        else -> "游戏结束"
                    },
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            } else {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "当前:",
                        fontSize = 16.sp
                    )
                    PlayerIndicator(stone = currentPlayer)
                    Text(
                        text = if (currentPlayer == Stone.BLACK) "黑方" else "白方",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                    if (isAiThinking) {
                        Text(
                            text = "(AI思考中...)",
                            fontSize = 14.sp,
                            color = Color.Gray
                        )
                    }
                }
            }

            Divider()

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceAround
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    PlayerIndicator(stone = Stone.BLACK, size = 24.dp)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text("提子: $blackCaptures", fontSize = 12.sp)
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    PlayerIndicator(stone = Stone.WHITE, size = 24.dp)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text("提子: $whiteCaptures", fontSize = 12.sp)
                }
            }
        }
    }
}

@Composable
fun PlayerIndicator(stone: Stone, size: androidx.compose.ui.unit.Dp = 16.dp) {
    val color = if (stone == Stone.BLACK) Color(0xFF1A1A1A) else Color(0xFFF5F5F5)
    val borderColor = if (stone == Stone.WHITE) Color.Gray else Color.Transparent

    androidx.compose.foundation.Canvas(
        modifier = Modifier.size(size)
    ) {
        drawCircle(
            color = color,
            radius = size.toPx() / 2
        )
        if (stone == Stone.WHITE) {
            drawCircle(
                color = borderColor.copy(alpha = 0.5f),
                radius = size.toPx() / 2,
                style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1.dp.toPx())
            )
        }
    }
}
