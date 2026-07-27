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

    private val workDir: File
        get() = context.filesDir

    private val binaryPath: String
        get() = File(context.applicationInfo.nativeLibraryDir, BINARY_NAME).absolutePath

    private val weightsPath: String
        get() = File(workDir, WEIGHTS_NAME).absolutePath

    suspend fun ensureEngineReady(): Result<Unit> = withContext(Dispatchers.IO) {
        synchronized(lock) {
            if (engineReady && isProcessAlive()) {
                return@synchronized Result.success(Unit)
            }

            try {
                prepareWeights()
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

    private fun prepareWeights() {
        val weightsFile = File(workDir, WEIGHTS_NAME)

        if (!weightsFile.exists()) {
            Log.d(TAG, "Copying weights from assets to ${weightsFile.absolutePath}...")
            if (assetExists(WEIGHTS_NAME)) {
                copyAsset(WEIGHTS_NAME, weightsFile)
            } else {
                throw IllegalStateException(
                    "Network weights not found in assets. " +
                    "Please place 'lz_network.lz' in app/src/main/assets/"
                )
            }
        }

        val binaryFile = File(binaryPath)
        Log.d(TAG, "Binary location: $binaryPath")
        Log.d(TAG, "Binary exists: ${binaryFile.exists()}")
        Log.d(TAG, "Binary canExecute: ${binaryFile.canExecute()}")
        Log.d(TAG, "Weights exists: ${weightsFile.exists()}")

        if (!binaryFile.exists()) {
            throw IllegalStateException(
                "Leela Zero binary not found at $binaryPath. " +
                "Make sure jniLibs/arm64-v8a/leelaz is included in the APK."
            )
        }

        if (!binaryFile.canExecute()) {
            Log.w(TAG, "Binary in nativeLibraryDir is not executable, trying chmod...")
            try {
                val chmodProcess = Runtime.getRuntime().exec(
                    arrayOf("chmod", "755", binaryFile.absolutePath)
                )
                chmodProcess.waitFor()
            } catch (_: Exception) {
            }
            if (!binaryFile.canExecute()) {
                throw IllegalStateException("Binary is not executable: $binaryPath")
            }
        }
    }

    private fun assetExists(assetName: String): Boolean {
        return try {
            context.assets.list("")?.contains(assetName) == true
        } catch (e: Exception) {
            false
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
        Log.d(TAG, "Work dir: ${workDir.absolutePath}")
        Log.d(TAG, "Binary exists: ${File(binaryPath).exists()}")
        Log.d(TAG, "Binary canExecute: ${File(binaryPath).canExecute()}")
        Log.d(TAG, "Weights exists: ${File(weightsPath).exists()}")

        val pb = ProcessBuilder(
            binaryPath,
            "--gtp",
            "--weights", weightsPath
        )
        pb.directory(workDir)
        pb.redirectErrorStream(false)

        try {
            process = pb.start()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start process", e)
            Log.e(TAG, "Error message: ${e.message}")
            drainErrorStream()
            throw e
        }

        writer = BufferedWriter(OutputStreamWriter(process!!.outputStream))
        reader = BufferedReader(InputStreamReader(process!!.inputStream))
        errorReader = BufferedReader(InputStreamReader(process!!.errorStream))

        isInitialized = true
        Log.d(TAG, "Process started successfully")
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
        private const val BINARY_NAME = "libleelaz.so"
        private const val WEIGHTS_NAME = "lz_network.lz"
        private const val DEFAULT_KOMI = 7.5
    }
}
