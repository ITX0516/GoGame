package com.example.gogame.engine

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.example.gogame.engine.gtp.GtpCommand
import com.example.gogame.engine.gtp.GtpResponse
import com.example.gogame.model.Move
import com.example.gogame.model.Stone
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

class LeelaManager(private val context: Context) : GoEngine {

    private var callback: EngineCallback? = null
    private val isReady = AtomicBoolean(false)
    private val isStarted = AtomicBoolean(false)
    private val pendingGenMoveColor = AtomicReference<Stone?>(null)
    private val mainHandler = Handler(Looper.getMainLooper())
    private var aiTimeoutRunnable: Runnable? = null

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
                    postError("Failed to start engine")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error starting engine", e)
                postError("Engine start error: ${e.message}")
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
            postOnMain {
                callback?.onEngineReady()
            }
            Log.d(TAG, "Engine initialized and ready")
        } catch (e: Exception) {
            Log.e(TAG, "Error initializing engine", e)
            postError("Init error: ${e.message}")
        }
    }

    override fun stop() {
        isReady.set(false)
        isStarted.set(false)
        cancelAiTimeout()
        try {
            nativeStopEngine()
            nativeDestroyEngine()
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping engine", e)
        }
    }

    override fun isReady(): Boolean = isReady.get()

    override fun setBoardSize(size: Int) {
        nativeSendCommand("boardsize $size")
    }

    override fun clearBoard() {
        nativeSendCommand("clear_board")
    }

    override fun playMove(move: Move) {
        val color = if (move.stone == Stone.BLACK) "b" else "w"
        val pos = move.toGtpPosition()
        nativeSendCommand("play $color $pos")
    }

    override fun genMove(stone: Stone) {
        if (pendingGenMoveColor.get() != null) {
            Log.w(TAG, "genMove called while another is pending")
            return
        }
        pendingGenMoveColor.set(stone)
        startAiTimeout()
        val color = if (stone == Stone.BLACK) "b" else "w"
        nativeSendCommand("genmove $color")
    }

    override fun undo() {
        nativeSendCommand("undo")
    }

    override fun setKomi(komi: Double) {
        nativeSendCommand("komi $komi")
    }

    override fun setTimeSettings(mainTime: Int, byoYomiTime: Int, byoYomiStones: Int) {
        nativeSendCommand("time_settings $mainTime $byoYomiTime $byoYomiStones")
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

        if (pendingGenMoveColor.get() != null) {
            handleGenMoveResponse(gtpResponse)
            return
        }

        postOnMain {
            callback?.onInfo(response.trim())
        }
    }

    private fun handleGenMoveResponse(response: GtpResponse) {
        cancelAiTimeout()
        val color = pendingGenMoveColor.getAndSet(null)

        if (!response.isSuccess || color == null) {
            postError("Genmove failed: ${response.content}")
            return
        }

        val moveStr = response.content.trim()
        try {
            val move = Move.fromGtpPosition(color, moveStr, 19)
            postOnMain {
                callback?.onMoveGenerated(move)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse move: $moveStr", e)
            postError("Failed to parse move: $moveStr")
        }
    }

    private fun sendCommandSync(command: String): GtpResponse {
        val responseStr = nativeSendCommandSync(command)
        return GtpResponse.parse(responseStr)
    }

    private fun startAiTimeout() {
        cancelAiTimeout()
        aiTimeoutRunnable = Runnable {
            Log.e(TAG, "AI move timed out")
            pendingGenMoveColor.set(null)
            postError("AI 思考超时")
        }
        mainHandler.postDelayed(aiTimeoutRunnable!!, AI_TIMEOUT_MS)
    }

    private fun cancelAiTimeout() {
        aiTimeoutRunnable?.let {
            mainHandler.removeCallbacks(it)
            aiTimeoutRunnable = null
        }
    }

    private fun postError(error: String) {
        postOnMain {
            callback?.onEngineError(error)
        }
    }

    private fun postOnMain(action: () -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            action()
        } else {
            mainHandler.post(action)
        }
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
        private const val AI_TIMEOUT_MS = 30000L
    }
}
