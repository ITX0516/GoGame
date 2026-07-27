package com.example.gogame.model

data class Move(
    val stone: Stone,
    val x: Int,
    val y: Int,
    val isPass: Boolean = false,
    val capturedStones: List<Pair<Int, Int>> = emptyList()
) {
    fun toGtpPosition(): String {
        if (isPass) return "pass"
        val colChar = 'A' + if (x >= 8) x + 1 else x
        val row = y + 1
        return "$colChar$row"
    }

    companion object {
        fun fromGtpPosition(stone: Stone, gtpPos: String, boardSize: Int): Move {
            if (gtpPos.equals("pass", ignoreCase = true)) {
                return Move(stone, -1, -1, isPass = true)
            }
            val colChar = gtpPos[0].uppercaseChar()
            val col = if (colChar > 'I') colChar - 'A' - 1 else colChar - 'A'
            val row = gtpPos.substring(1).toInt() - 1
            return Move(stone, col, row)
        }
    }
}
