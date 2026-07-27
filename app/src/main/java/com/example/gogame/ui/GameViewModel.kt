package com.example.gogame.ui

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.example.gogame.engine.EngineCallback
import com.example.gogame.engine.LeelaManager
import com.example.gogame.model.GoGame
import com.example.gogame.model.Move
import com.example.gogame.model.Stone
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

    private var leelaManager: LeelaManager? = null

    val game: GoGame
        get() = _game

    init {
        updateUiState()
    }

    fun initEngine() {
        if (leelaManager != null) return

        leelaManager = LeelaManager(context.applicationContext)
        leelaManager?.setCallback(object : EngineCallback {
            override fun onEngineReady() {
                _uiState.value = _uiState.value.copy(
                    engineReady = true,
                    engineError = null
                )
                if (_uiState.value.gameMode == GameMode.PVE &&
                    _uiState.value.currentPlayer == _uiState.value.aiColor &&
                    !_game.isGameOver()
                ) {
                    requestAiMove()
                }
            }

            override fun onEngineError(error: String) {
                _uiState.value = _uiState.value.copy(
                    engineReady = false,
                    engineError = error,
                    isAiThinking = false
                )
            }

            override fun onMoveGenerated(move: Move) {
                applyAiMove(move)
            }

            override fun onInfo(info: String) {
            }
        })
        leelaManager?.start()
    }

    private fun ensureEngineStarted(): Boolean {
        if (leelaManager == null) {
            initEngine()
            return false
        }
        return leelaManager?.isReady() == true
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
            syncEngineBoard()
            checkAndRequestAiMove()
        }
        return result
    }

    fun pass() {
        if (_uiState.value.isAiThinking) return
        _game.pass()
        updateUiState()
        syncEngineBoard()
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
            if (leelaManager?.isReady() == true) {
                leelaManager?.undo()
                leelaManager?.undo()
            }
        }
        updateUiState()
        return result
    }

    fun newGame() {
        if (_uiState.value.isAiThinking) return
        _game.reset()
        updateUiState()
        if (leelaManager?.isReady() == true) {
            leelaManager?.clearBoard()
        }
    }

    fun setGameMode(mode: GameMode) {
        _uiState.value = _uiState.value.copy(gameMode = mode)
        newGame()
    }

    fun setAiColor(color: Stone) {
        _uiState.value = _uiState.value.copy(aiColor = color)
    }

    fun setAiThinking(thinking: Boolean) {
        _uiState.value = _uiState.value.copy(isAiThinking = thinking)
    }

    fun applyAiMove(move: Move) {
        if (move.isPass) {
            _game.pass()
        } else {
            _game.placeStone(move.x, move.y)
        }
        setAiThinking(false)
        updateUiState()
    }

    private fun checkAndRequestAiMove() {
        if (_uiState.value.gameMode != GameMode.PVE) return
        if (_game.isGameOver()) return
        if (_uiState.value.currentPlayer != _uiState.value.aiColor) return
        if (_uiState.value.isAiThinking) return

        if (!ensureEngineStarted()) return

        requestAiMove()
    }

    private fun requestAiMove() {
        setAiThinking(true)
        leelaManager?.genMove(_uiState.value.aiColor)
    }

    private fun syncEngineBoard() {
        if (leelaManager?.isReady() != true) return

        val lastMove = _game.getLastMove() ?: return
        leelaManager?.playMove(lastMove)
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
        leelaManager?.stop()
        leelaManager = null
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
