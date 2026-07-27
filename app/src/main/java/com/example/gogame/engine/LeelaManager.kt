package com.example.gogame.engine

import android.content.Context
import android.util.Log
import com.example.gogame.engine.gtp.GtpCommand
import com.example.gogame.engine.gtp.GtpResponse
import com.example.gogame.model.Move
import com.example.gogame.model.Stone
import java.util.concurrent.atomic.AtomicBoolean

class LeelaManager(private val context: Context) : GoEngine {

    private var callback: EngineCallback? = null
    private val isReady = AtomicBoolean(false)
    private val isStarted = AtomicBoolean(false)
    private var pendingGenMoveColor: Stone? = null

    init {
        try {
            System.loadLibrary("gogame")
            Log.d(TAG, "Native library loaded successfully")
        } catch (e: UnsatisfiedLinkError) {
            Log.e(TAG, "Failed to load native library", e)
        }
    }

    override fun start() {
        if (isStarted.get()) {
            Log.w(TAG, "Engine already started")
            return
        }

        Thread {
            try {
                nativeInitEngine()
                val success = nativeStartEngine()
                if (success) {
                    isStarted.set(true)
                    initializeEngine()
                } else {
                    callback?.onEngineError("Failed to start engine")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error starting engine", e)
                callback?.onEngineError("Engine start error: ${e.message}")
            }
        }.start()
    }

    private fun initializeEngine() {
        try {
            sendCommandSync("protocol_version")
            sendCommandSync("name")
            sendCommandSync("version")
            sendCommandSync("boardsize 19")
            sendCommandSync("clear_board")
            sendCommandSync("komi 6.5")

            isReady.set(true)
            callback?.onEngineReady()
            Log.d(TAG, "Engine initialized and ready")
        } catch (e: Exception) {
            Log.e(TAG, "Error initializing engine", e)
            callback?.onEngineError("Init error: ${e.message}")
        }
    }

    override fun stop() {
        isReady.set(false)
        isStarted.set(false)
        try {
            nativeStopEngine()
            nativeDestroyEngine()
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping engine", e)
        }
    }

    override fun isReady(): Boolean = isReady.get()

    override fun setBoardSize(size: Int) {
        sendCommand("boardsize $size")
    }

    override fun clearBoard() {
        sendCommand("clear_board")
    }

    override fun playMove(move: Move) {
        val color = if (move.stone == Stone.BLACK) "b" else "w"
        val pos = move.toGtpPosition()
        sendCommand("play $color $pos")
    }

    override fun genMove(stone: Stone) {
        pendingGenMoveColor = stone
        val color = if (stone == Stone.BLACK) "b" else "w"
        sendCommand("genmove $color")
    }

    override fun undo() {
        sendCommand("undo")
    }

    override fun setKomi(komi: Double) {
        sendCommand("komi $komi")
    }

    override fun setTimeSettings(mainTime: Int, byoYomiTime: Int, byoYomiStones: Int) {
        sendCommand("time_settings $mainTime $byoYomiTime $byoYomiStones")
    }

    override fun sendCommand(command: String): String {
        return if (isReady.get()) {
            nativeSendCommandSync(command)
        } else {
            "? engine not ready\n\n"
        }
    }

    override fun setCallback(callback: EngineCallback?) {
        this.callback = callback
    }

    override fun getEngineName(): String {
        return try {
            nativeGetEngineName()
        } catch (e: Exception) {
            "Unknown"
        }
    }

    override fun getEngineVersion(): String {
        return try {
            nativeGetEngineVersion()
        } catch (e: Exception) {
            "0.0"
        }
    }

    fun onNativeResponse(response: String) {
        Log.d(TAG, "Native response: ${response.trim()}")

        val gtpResponse = GtpResponse.parse(response)

        if (pendingGenMoveColor != null) {
            handleGenMoveResponse(gtpResponse)
            return
        }

        callback?.onInfo(response.trim())
    }

    private fun handleGenMoveResponse(response: GtpResponse) {
        val color = pendingGenMoveColor
        pendingGenMoveColor = null

        if (!response.isSuccess || color == null) {
            callback?.onEngineError("Genmove failed: ${response.content}")
            return
        }

        val moveStr = response.content.trim()
        try {
            val move = Move.fromGtpPosition(color, moveStr, 19)
            callback?.onMoveGenerated(move)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse move: $moveStr", e)
            callback?.onEngineError("Failed to parse move: $moveStr")
        }
    }

    private fun sendCommandSync(command: String): GtpResponse {
        val responseStr = nativeSendCommandSync(command)
        return GtpResponse.parse(responseStr)
    }

    private external fun nativeInitEngine()
    private external fun nativeStartEngine(): Boolean
    private external fun nativeStopEngine()
    private external fun nativeIsEngineReady(): Boolean
    private external fun nativeSendCommand(command: String)
    private external fun nativeSendCommandSync(command: String): String
    private external fun nativeDestroyEngine()
    private external fun nativeGetEngineName(): String
    private external fun nativeGetEngineVersion(): String

    companion object {
        private const val TAG = "LeelaManager"
    }
}
