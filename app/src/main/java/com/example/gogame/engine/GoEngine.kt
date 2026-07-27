package com.example.gogame.engine

import com.example.gogame.engine.gtp.GtpCommand
import com.example.gogame.engine.gtp.GtpResponse
import com.example.gogame.model.Move
import com.example.gogame.model.Stone

interface EngineCallback {
    fun onEngineReady()
    fun onEngineError(error: String)
    fun onMoveGenerated(move: Move)
    fun onInfo(info: String)
}

interface GoEngine {
    fun start()
    fun stop()
    fun isReady(): Boolean
    fun setBoardSize(size: Int)
    fun clearBoard()
    fun playMove(move: Move)
    fun genMove(stone: Stone)
    fun undo()
    fun setKomi(komi: Double)
    fun setTimeSettings(mainTime: Int, byoYomiTime: Int, byoYomiStones: Int)
    fun sendCommand(command: String): String
    fun setCallback(callback: EngineCallback?)
    fun getEngineName(): String
    fun getEngineVersion(): String
}
