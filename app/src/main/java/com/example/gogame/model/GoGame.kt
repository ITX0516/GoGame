package com.example.gogame.model

class GoGame(private val boardSize: Int = 19) {
    private val board = BoardState(boardSize)
    private val moveHistory = mutableListOf<Move>()
    private val boardHistory = mutableListOf<String>()
    private var currentPlayer = Stone.BLACK
    private var consecutivePasses = 0
    private var gameOver = false
    private var blackCaptures = 0
    private var whiteCaptures = 0
    private var komi = 6.5

    fun getBoard(): BoardState = board

    fun getCurrentPlayer(): Stone = currentPlayer

    fun getMoveHistory(): List<Move> = moveHistory.toList()

    fun isGameOver(): Boolean = gameOver

    fun getBlackCaptures(): Int = blackCaptures

    fun getWhiteCaptures(): Int = whiteCaptures

    fun getKomi(): Double = komi

    fun setKomi(k: Double) {
        komi = k
    }

    fun canPlaceStone(x: Int, y: Int): Boolean {
        if (gameOver) return false
        if (x < 0 || x >= boardSize || y < 0 || y >= boardSize) return false
        if (board.getStone(x, y) != Stone.EMPTY) return false

        val testBoard = board.copy()
        testBoard.setStone(x, y, currentPlayer)
        val captured = findCaptures(testBoard, x, y, currentPlayer.opposite())

        for ((cx, cy) in captured) {
            testBoard.setStone(cx, cy, Stone.EMPTY)
        }

        val ownGroup = testBoard.getGroup(x, y)
        if (testBoard.getLiberties(ownGroup) == 0 && captured.isEmpty()) {
            return false
        }

        val boardString = testBoard.toBoardString()
        if (boardString in boardHistory) {
            return false
        }

        return true
    }

    fun placeStone(x: Int, y: Int): Boolean {
        if (!canPlaceStone(x, y)) return false

        board.setStone(x, y, currentPlayer)
        val captured = findCaptures(board, x, y, currentPlayer.opposite())

        for ((cx, cy) in captured) {
            board.setStone(cx, cy, Stone.EMPTY)
        }

        if (currentPlayer == Stone.BLACK) {
            blackCaptures += captured.size
        } else {
            whiteCaptures += captured.size
        }

        val move = Move(currentPlayer, x, y, false, captured)
        moveHistory.add(move)
        boardHistory.add(board.toBoardString())
        currentPlayer = currentPlayer.opposite()
        consecutivePasses = 0

        return true
    }

    fun pass() {
        if (gameOver) return

        val move = Move(currentPlayer, -1, -1, isPass = true)
        moveHistory.add(move)
        consecutivePasses++
        currentPlayer = currentPlayer.opposite()

        if (consecutivePasses >= 2) {
            gameOver = true
        }
    }

    fun resign() {
        gameOver = true
    }

    fun undo(): Boolean {
        if (moveHistory.isEmpty()) return false

        val lastMove = moveHistory.removeLast()
        if (boardHistory.isNotEmpty()) {
            boardHistory.removeLast()
        }

        if (!lastMove.isPass) {
            board.setStone(lastMove.x, lastMove.y, Stone.EMPTY)

            for ((cx, cy) in lastMove.capturedStones) {
                board.setStone(cx, cy, lastMove.stone.opposite())
            }

            if (lastMove.stone == Stone.BLACK) {
                blackCaptures -= lastMove.capturedStones.size
            } else {
                whiteCaptures -= lastMove.capturedStones.size
            }
        } else {
            consecutivePasses = (consecutivePasses - 1).coerceAtLeast(0)
        }

        currentPlayer = lastMove.stone
        gameOver = false
        return true
    }

    fun reset() {
        board.clear()
        moveHistory.clear()
        boardHistory.clear()
        currentPlayer = Stone.BLACK
        consecutivePasses = 0
        gameOver = false
        blackCaptures = 0
        whiteCaptures = 0
    }

    fun getWinner(): Stone? {
        if (!gameOver) return null
        val blackScore = calculateScore(Stone.BLACK)
        val whiteScore = calculateScore(Stone.WHITE) + komi
        return if (blackScore > whiteScore) Stone.BLACK else Stone.WHITE
    }

    fun calculateScore(stone: Stone): Double {
        var territory = 0
        val visited = mutableSetOf<Pair<Int, Int>>()

        for (x in 0 until boardSize) {
            for (y in 0 until boardSize) {
                if (board.getStone(x, y) == Stone.EMPTY && (x to y) !in visited) {
                    val (area, borders) = floodFillEmpty(x, y, visited)
                    val borderColors = borders.map { board.getStone(it.first, it.second) }.toSet()
                    if (borderColors.size == 1 && borderColors.first() == stone) {
                        territory += area
                    }
                }
            }
        }

        return (board.countStones(stone) + territory).toDouble()
    }

    private fun floodFillEmpty(
        startX: Int,
        startY: Int,
        visited: MutableSet<Pair<Int, Int>>
    ): Pair<Int, MutableSet<Pair<Int, Int>>> {
        var area = 0
        val borders = mutableSetOf<Pair<Int, Int>>()
        val queue = ArrayDeque<Pair<Int, Int>>()
        queue.add(startX to startY)
        visited.add(startX to startY)

        while (queue.isNotEmpty()) {
            val (x, y) = queue.removeFirst()
            area++

            for ((dx, dy) in listOf(-1 to 0, 1 to 0, 0 to -1, 0 to 1)) {
                val nx = x + dx
                val ny = y + dy
                if (nx < 0 || nx >= boardSize || ny < 0 || ny >= boardSize) continue

                val pos = nx to ny
                if (board.getStone(nx, ny) == Stone.EMPTY) {
                    if (pos !in visited) {
                        visited.add(pos)
                        queue.add(pos)
                    }
                } else {
                    borders.add(pos)
                }
            }
        }
        return area to borders
    }

    private fun findCaptures(
        boardState: BoardState,
        lastX: Int,
        lastY: Int,
        opponent: Stone
    ): List<Pair<Int, Int>> {
        val captured = mutableListOf<Pair<Int, Int>>()
        val checked = mutableSetOf<Pair<Int, Int>>()

        for ((dx, dy) in listOf(-1 to 0, 1 to 0, 0 to -1, 0 to 1)) {
            val nx = lastX + dx
            val ny = lastY + dy
            val pos = nx to ny

            if (nx in 0 until boardSize && ny in 0 until boardSize &&
                boardState.getStone(nx, ny) == opponent && pos !in checked
            ) {
                val group = boardState.getGroup(nx, ny)
                checked.addAll(group)
                if (boardState.getLiberties(group) == 0) {
                    captured.addAll(group)
                }
            }
        }
        return captured
    }

    fun getLastMove(): Move? = moveHistory.lastOrNull()
}
