package com.example.gogame.model

enum class Stone {
    BLACK, WHITE, EMPTY;

    fun opposite(): Stone = when (this) {
        BLACK -> WHITE
        WHITE -> BLACK
        EMPTY -> EMPTY
    }
}
