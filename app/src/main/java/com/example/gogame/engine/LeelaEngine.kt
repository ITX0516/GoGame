package com.example.gogame.engine

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.File
import java.io.InputStreamReader
import java.io.OutputStreamWriter

class LeelaEngine(private val context: Context) {

    private var process: Process? = null
    private var writer: BufferedWriter? = null
    private var reader: BufferedReader? = null
    private var errorReader: BufferedReader? = null
    private val lock = Any()
    @Volatile private var isInitialized = false
    @Volatile private var engineReady = false

    private val filesDir: File
        get() = context.filesDir

    private val binaryPath: String
        get() = File(filesDir, BINARY_NAME).absolutePath

    private val weightsPath: String
        get() = File(filesDir, WEIGHTS_NAME).absolutePath

    suspend fun ensureEngineReady(): Result<Unit> = withContext(Dispatchers.IO) {
        synchronized(lock) {
            if (engineReady && isProcessAlive()) {
                return@synchronized Result.success(Unit)
            }

            try {
                copyAssetsIfNeeded()
                startProcess()
                sendCommandSync("boardsize 19")
                sendCommandSync("komi $DEFAULT_KOMI")
                sendCommandSync("clear_board")
                engineReady = true
                Log.d(TAG, "Engine ready")
                Result.success(Unit)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to start engine", e)
                destroyProcess()
                Result.failure(e)
            }
        }
    }

    private fun copyAssetsIfNeeded() {
        val binaryFile = File(filesDir, BINARY_NAME)
        val weightsFile = File(filesDir, WEIGHTS_NAME)

        if (!binaryFile.exists()) {
            Log.d(TAG, "Copying binary from assets...")
            copyAsset(BINARY_NAME, binaryFile)
            binaryFile.setExecutable(true)
        }

        if (!weightsFile.exists()) {
            Log.d(TAG, "Copying weights from assets...")
            copyAsset(WEIGHTS_NAME, weightsFile)
        }
    }

    private fun copyAsset(assetName: String, destFile: File) {
        context.assets.open(assetName).use { input ->
            destFile.outputStream().use { output ->
                input.copyTo(output)
            }
        }
    }

    private fun startProcess() {
        Log.d(TAG, "Starting leelaz process...")
        Log.d(TAG, "Binary: $binaryPath")
        Log.d(TAG, "Weights: $weightsPath")

        val pb = ProcessBuilder(
            binaryPath,
            "--gtp",
            "--weights", weightsPath
        )
        pb.directory(filesDir)
        pb.redirectErrorStream(false)

        process = pb.start()
        writer = BufferedWriter(OutputStreamWriter(process!!.outputStream))
        reader = BufferedReader(InputStreamReader(process!!.inputStream))
        errorReader = BufferedReader(InputStreamReader(process!!.errorStream))

        isInitialized = true
        Log.d(TAG, "Process started")
    }

    suspend fun genmove(color: String): Result<String> = withContext(Dispatchers.IO) {
        synchronized(lock) {
            try {
                val response = sendCommandSync("genmove $color")
                val move = parseGtpResponse(response)
                Log.d(TAG, "genmove $color = $move")
                Result.success(move)
            } catch (e: Exception) {
                Log.e(TAG, "genmove failed", e)
                Result.failure(e)
            }
        }
    }

    suspend fun playMove(color: String, coord: String): Result<Unit> = withContext(Dispatchers.IO) {
        synchronized(lock) {
            try {
                sendCommandSync("play $color $coord")
                Result.success(Unit)
            } catch (e: Exception) {
                Log.e(TAG, "playMove failed", e)
                Result.failure(e)
            }
        }
    }

    suspend fun undo(): Result<Unit> = withContext(Dispatchers.IO) {
        synchronized(lock) {
            try {
                sendCommandSync("undo")
                Result.success(Unit)
            } catch (e: Exception) {
                Log.e(TAG, "undo failed", e)
                Result.failure(e)
            }
        }
    }

    suspend fun clearBoard(): Result<Unit> = withContext(Dispatchers.IO) {
        synchronized(lock) {
            try {
                sendCommandSync("clear_board")
                Result.success(Unit)
            } catch (e: Exception) {
                Log.e(TAG, "clearBoard failed", e)
                Result.failure(e)
            }
        }
    }

    private fun sendCommandSync(command: String): String {
        if (!isProcessAlive()) {
            throw IllegalStateException("Engine process is not running")
        }

        Log.d(TAG, "Sending: $command")

        writer?.write(command)
        writer?.newLine()
        writer?.flush()

        val response = readGtpResponse()
        Log.d(TAG, "Response: $response")

        if (response.startsWith("?")) {
            throw GtpException(response.substring(1).trim())
        }

        return response
    }

    private fun readGtpResponse(): String {
        val sb = StringBuilder()
        var line: String? = reader?.readLine()

        if (line == null) {
            drainErrorStream()
            throw IllegalStateException("Engine closed stdout")
        }

        sb.append(line)
        sb.append('\n')

        while (true) {
            line = reader?.readLine()
            if (line == null) break
            if (line.isEmpty()) break
            sb.append(line)
            sb.append('\n')
        }

        return sb.toString().trim()
    }

    private fun parseGtpResponse(response: String): String {
        if (response.startsWith("=")) {
            return response.substring(1).trim()
        }
        throw GtpException(response)
    }

    private fun drainErrorStream() {
        try {
            val errors = StringBuilder()
            while (errorReader?.ready() == true) {
                val line = errorReader?.readLine() ?: break
                errors.append(line).append('\n')
            }
            if (errors.isNotEmpty()) {
                Log.e(TAG, "Stderr: $errors")
            }
        } catch (_: Exception) {
        }
    }

    private fun isProcessAlive(): Boolean {
        return process?.isAlive == true && isInitialized
    }

    fun destroy() {
        synchronized(lock) {
            try {
                if (isProcessAlive()) {
                    try {
                        writer?.write("quit")
                        writer?.newLine()
                        writer?.flush()
                        Thread.sleep(100)
                    } catch (_: Exception) {
                    }
                }
            } finally {
                destroyProcess()
            }
        }
    }

    private fun destroyProcess() {
        try {
            process?.destroy()
            process?.waitFor()
        } catch (_: Exception) {
        }
        process = null
        writer = null
        reader = null
        errorReader = null
        engineReady = false
        isInitialized = false
    }

    fun isReady(): Boolean = engineReady && isProcessAlive()

    class GtpException(message: String) : Exception(message)

    companion object {
        private const val TAG = "LeelaEngine"
        private const val BINARY_NAME = "leelaz"
        private const val WEIGHTS_NAME = "lz_network.gz"
        private const val DEFAULT_KOMI = 7.5
    }
}
