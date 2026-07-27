package com.example.gogame.model

class BoardState(val size: Int = 19) {
    private val board = Array(size) { Array(size) { Stone.EMPTY } }

    fun getStone(x: Int, y: Int): Stone {
        if (x < 0 || x >= size || y < 0 || y >= size) return Stone.EMPTY
        return board[x][y]
    }

    fun setStone(x: Int, y: Int, stone: Stone) {
        if (x in 0 until size && y in 0 until size) {
            board[x][y] = stone
        }
    }

    fun clear() {
        for (x in 0 until size) {
            for (y in 0 until size) {
                board[x][y] = Stone.EMPTY
            }
        }
    }

    fun copy(): BoardState {
        val newState = BoardState(size)
        for (x in 0 until size) {
            for (y in 0 until size) {
                newState.board[x][y] = board[x][y]
            }
        }
        return newState
    }

    fun getGroup(x: Int, y: Int): List<Pair<Int, Int>> {
        val stone = getStone(x, y)
        if (stone == Stone.EMPTY) return emptyList()

        val group = mutableListOf<Pair<Int, Int>>()
        val visited = mutableSetOf<Pair<Int, Int>>()
        val queue = ArrayDeque<Pair<Int, Int>>()
        queue.add(x to y)
        visited.add(x to y)

        while (queue.isNotEmpty()) {
            val (cx, cy) = queue.removeFirst()
            group.add(cx to cy)

            for ((dx, dy) in listOf(-1 to 0, 1 to 0, 0 to -1, 0 to 1)) {
                val nx = cx + dx
                val ny = cy + dy
                val pos = nx to ny
                if (pos !in visited && getStone(nx, ny) == stone) {
                    visited.add(pos)
                    queue.add(pos)
                }
            }
        }
        return group
    }

    fun getLiberties(group: List<Pair<Int, Int>>): Int {
        val liberties = mutableSetOf<Pair<Int, Int>>()
        for ((x, y) in group) {
            for ((dx, dy) in listOf(-1 to 0, 1 to 0, 0 to -1, 0 to 1)) {
                val nx = x + dx
                val ny = y + dy
                if (nx in 0 until size && ny in 0 until size && getStone(nx, ny) == Stone.EMPTY) {
                    liberties.add(nx to ny)
                }
            }
        }
        return liberties.size
    }

    fun countStones(stone: Stone): Int {
        var count = 0
        for (x in 0 until size) {
            for (y in 0 until size) {
                if (board[x][y] == stone) count++
            }
        }
        return count
    }

    fun toBoardString(): String {
        val sb = StringBuilder()
        for (y in 0 until size) {
            for (x in 0 until size) {
                sb.append(
                    when (board[x][y]) {
                        Stone.BLACK -> 'X'
                        Stone.WHITE -> 'O'
                        Stone.EMPTY -> '.'
                    }
                )
            }
            sb.append('\n')
        }
        return sb.toString()
    }
}
