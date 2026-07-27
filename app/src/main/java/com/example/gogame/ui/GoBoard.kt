package com.example.gogame.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.example.gogame.model.BoardState
import com.example.gogame.model.Move
import com.example.gogame.model.Stone

@Composable
fun GoBoard(
    boardState: BoardState,
    lastMove: Move?,
    boardSize: Int,
    onCellClick: (Int, Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val boardColor = Color(0xFFDEB887)
    val lineColor = Color.Black
    val blackStoneColor = Color(0xFF1A1A1A)
    val whiteStoneColor = Color(0xFFF5F5F5)
    val starPointColor = Color.Black

    BoxWithConstraints(modifier = modifier) {
        val boardWidth = minOf(maxWidth, maxHeight)
        val padding = with(LocalDensity.current) { 20.dp.toPx() }
        val usableWidth = boardWidth.value * LocalDensity.current.density - padding * 2
        val cellSize = usableWidth / (boardSize - 1)

        Canvas(
            modifier = Modifier
                .size(boardWidth)
                .pointerInput(Unit) {
                    detectTapGestures { offset ->
                        val x = ((offset.x - padding) / cellSize + 0.5f).toInt()
                        val y = ((offset.y - padding) / cellSize + 0.5f).toInt()
                        if (x in 0 until boardSize && y in 0 until boardSize) {
                            onCellClick(x, y)
                        }
                    }
                }
        ) {
            drawBoard(
                boardColor = boardColor,
                lineColor = lineColor,
                starPointColor = starPointColor,
                padding = padding,
                cellSize = cellSize,
                boardSize = boardSize
            )

            drawStones(
                boardState = boardState,
                blackStoneColor = blackStoneColor,
                whiteStoneColor = whiteStoneColor,
                padding = padding,
                cellSize = cellSize,
                boardSize = boardSize,
                lastMove = lastMove
            )
        }
    }
}

private fun DrawScope.drawBoard(
    boardColor: Color,
    lineColor: Color,
    starPointColor: Color,
    padding: Float,
    cellSize: Float,
    boardSize: Int
) {
    val strokeWidth = 2f

    for (i in 0 until boardSize) {
        val pos = padding + i * cellSize
        drawLine(
            color = lineColor,
            start = Offset(padding, pos),
            end = Offset(padding + (boardSize - 1) * cellSize, pos),
            strokeWidth = strokeWidth
        )
        drawLine(
            color = lineColor,
            start = Offset(pos, padding),
            end = Offset(pos, padding + (boardSize - 1) * cellSize),
            strokeWidth = strokeWidth
        )
    }

    val starPoints = getStarPoints(boardSize)
    for ((sx, sy) in starPoints) {
        drawCircle(
            color = starPointColor,
            radius = 4f,
            center = Offset(
                padding + sx * cellSize,
                padding + sy * cellSize
            )
        )
    }
}

private fun DrawScope.drawStones(
    boardState: BoardState,
    blackStoneColor: Color,
    whiteStoneColor: Color,
    padding: Float,
    cellSize: Float,
    boardSize: Int,
    lastMove: Move?
) {
    val stoneRadius = cellSize * 0.45f

    for (x in 0 until boardSize) {
        for (y in 0 until boardSize) {
            val stone = boardState.getStone(x, y)
            if (stone != Stone.EMPTY) {
                val cx = padding + x * cellSize
                val cy = padding + y * cellSize

                drawCircle(
                    color = if (stone == Stone.BLACK) blackStoneColor else whiteStoneColor,
                    radius = stoneRadius,
                    center = Offset(cx, cy)
                )

                if (stone == Stone.WHITE) {
                    drawCircle(
                        color = Color.Gray.copy(alpha = 0.3f),
                        radius = stoneRadius,
                        center = Offset(cx, cy)
                    )
                }
            }
        }
    }

    if (lastMove != null && !lastMove.isPass) {
        val cx = padding + lastMove.x * cellSize
        val cy = padding + lastMove.y * cellSize
        val markColor = if (lastMove.stone == Stone.BLACK) Color.White else Color.Black
        drawCircle(
            color = markColor,
            radius = stoneRadius * 0.25f,
            center = Offset(cx, cy)
        )
    }
}

private fun getStarPoints(boardSize: Int): List<Pair<Int, Int>> {
    return when (boardSize) {
        9 -> listOf(2 to 2, 6 to 2, 4 to 4, 2 to 6, 6 to 6)
        13 -> listOf(3 to 3, 9 to 3, 6 to 6, 3 to 9, 9 to 9)
        19 -> listOf(
            3 to 3, 9 to 3, 15 to 3,
            3 to 9, 9 to 9, 15 to 9,
            3 to 15, 9 to 15, 15 to 15
        )
        else -> emptyList()
    }
}
