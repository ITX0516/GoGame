package com.example.gogame.ui

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.gogame.engine.LeelaEngine
import com.example.gogame.model.GoGame
import com.example.gogame.model.Move
import com.example.gogame.model.Stone
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class GameUiState(
    val boardSize: Int = 19,
    val currentPlayer: Stone = Stone.BLACK,
    val blackCaptures: Int = 0,
    val whiteCaptures: Int = 0,
    val lastMove: Move? = null,
    val isGameOver: Boolean = false,
    val winner: Stone? = null,
    val isAiThinking: Boolean = false,
    val gameMode: GameMode = GameMode.PVP,
    val aiColor: Stone = Stone.WHITE,
    val moveCount: Int = 0,
    val engineReady: Boolean = false,
    val engineError: String? = null
)

enum class GameMode {
    PVP, PVE
}

class GameViewModel(private val context: Context) : ViewModel() {
    private val _game = GoGame(19)
    private val _uiState = MutableStateFlow(GameUiState())
    val uiState: StateFlow<GameUiState> = _uiState.asStateFlow()

    private var leelaEngine: LeelaEngine? = null
    private var aiJob: Job? = null

    val game: GoGame
        get() = _game

    init {
        updateUiState()
    }

    private fun ensureEngine(): LeelaEngine {
        if (leelaEngine == null) {
            leelaEngine = LeelaEngine(context.applicationContext)
        }
        return leelaEngine!!
    }

    fun placeStone(x: Int, y: Int): Boolean {
        if (_uiState.value.isAiThinking) return false
        if (_uiState.value.gameMode == GameMode.PVE &&
            _uiState.value.currentPlayer == _uiState.value.aiColor
        ) {
            return false
        }

        val result = _game.placeStone(x, y)
        if (result) {
            updateUiState()
            checkAndRequestAiMove()
        }
        return result
    }

    fun pass() {
        if (_uiState.value.isAiThinking) return
        _game.pass()
        updateUiState()
        checkAndRequestAiMove()
    }

    fun resign() {
        if (_uiState.value.isAiThinking) return
        _game.resign()
        updateUiState()
    }

    fun undo(): Boolean {
        if (_uiState.value.isAiThinking) return false
        val result = _game.undo()
        if (result && _uiState.value.gameMode == GameMode.PVE) {
            _game.undo()
            syncUndo()
        }
        updateUiState()
        return result
    }

    fun newGame() {
        if (_uiState.value.isAiThinking) return
        _game.reset()
        updateUiState()
        syncClearBoard()
    }

    fun setGameMode(mode: GameMode) {
        _uiState.value = _uiState.value.copy(gameMode = mode)
        newGame()
    }

    fun setAiColor(color: Stone) {
        _uiState.value = _uiState.value.copy(aiColor = color)
    }

    private fun checkAndRequestAiMove() {
        if (_uiState.value.gameMode != GameMode.PVE) return
        if (_game.isGameOver()) return
        if (_uiState.value.currentPlayer != _uiState.value.aiColor) return
        if (_uiState.value.isAiThinking) return

        requestAiMove()
    }

    private fun requestAiMove() {
        aiJob?.cancel()
        aiJob = viewModelScope.launch(Dispatchers.IO) {
            setAiThinking(true)
            clearEngineError()

            try {
                val engine = ensureEngine()
                val readyResult = engine.ensureEngineReady()
                if (readyResult.isFailure) {
                    throw readyResult.exceptionOrNull() ?: RuntimeException("Engine failed to start")
                }

                syncBoardToEngine()

                val color = gtpColor(_uiState.value.aiColor)
                val moveResult = engine.genmove(color)
                if (moveResult.isFailure) {
                    throw moveResult.exceptionOrNull() ?: RuntimeException("genmove failed")
                }

                val moveStr = moveResult.getOrThrow()
                val move = Move.fromGtpPosition(_uiState.value.aiColor, moveStr, _game.getBoard().size)

                withContext(Dispatchers.Main) {
                    applyAiMove(move)
                }

            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    setEngineError(e.message ?: "AI 引擎错误")
                    setAiThinking(false)
                }
            }
        }
    }

    private suspend fun syncBoardToEngine() {
        val engine = ensureEngine()
        val history = _game.getMoveHistory()

        engine.clearBoard().onFailure {
            throw it
        }

        for (move in history) {
            if (!move.isPass) {
                val color = gtpColor(move.stone)
                val pos = move.toGtpPosition()
                engine.playMove(color, pos).onFailure { e ->
                    throw e
                }
            }
        }
    }

    private fun syncUndo() {
        if (_uiState.value.gameMode != GameMode.PVE) return
        aiJob?.cancel()
        aiJob = viewModelScope.launch(Dispatchers.IO) {
            try {
                val engine = ensureEngine()
                if (!engine.isReady()) return@launch
                engine.undo()
                engine.undo()
            } catch (_: Exception) {
            }
        }
    }

    private fun syncClearBoard() {
        if (_uiState.value.gameMode != GameMode.PVE) return
        aiJob?.cancel()
        aiJob = viewModelScope.launch(Dispatchers.IO) {
            try {
                val engine = ensureEngine()
                if (!engine.isReady()) return@launch
                engine.clearBoard()
            } catch (_: Exception) {
            }
        }
    }

    private fun applyAiMove(move: Move) {
        if (move.isPass) {
            _game.pass()
        } else {
            _game.placeStone(move.x, move.y)
        }
        setAiThinking(false)
        updateUiState()
    }

    private fun setAiThinking(thinking: Boolean) {
        _uiState.value = _uiState.value.copy(isAiThinking = thinking)
    }

    private fun setEngineError(error: String) {
        _uiState.value = _uiState.value.copy(
            engineError = error,
            engineReady = false
        )
    }

    private fun clearEngineError() {
        _uiState.value = _uiState.value.copy(engineError = null)
    }

    private fun gtpColor(stone: Stone): String = when (stone) {
        Stone.BLACK -> "b"
        Stone.WHITE -> "w"
        Stone.EMPTY -> "b"
    }

    private fun updateUiState() {
        _uiState.value = _uiState.value.copy(
            currentPlayer = _game.getCurrentPlayer(),
            blackCaptures = _game.getBlackCaptures(),
            whiteCaptures = _game.getWhiteCaptures(),
            lastMove = _game.getLastMove(),
            isGameOver = _game.isGameOver(),
            winner = _game.getWinner(),
            moveCount = _game.getMoveHistory().size
        )
    }

    override fun onCleared() {
        super.onCleared()
        aiJob?.cancel()
        leelaEngine?.destroy()
        leelaEngine = null
    }

    class Factory(private val context: Context) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(GameViewModel::class.java)) {
                @Suppress("UNCHECKED_CAST")
                return GameViewModel(context) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class")
        }
    }
}
