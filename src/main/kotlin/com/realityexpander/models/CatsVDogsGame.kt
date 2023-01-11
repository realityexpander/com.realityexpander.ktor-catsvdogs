package com.realityexpander.models

import io.ktor.websocket.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.util.concurrent.ConcurrentHashMap

class CatsVDogsGame {

    private val state = MutableStateFlow(GameState())

    private val playerSockets = ConcurrentHashMap<Char, WebSocketSession>()
    private val gameScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var delayGameJob: Job? = null

    init {
        state.onEach(::broadcast).launchIn(gameScope)
    }

    fun connectPlayer(session: WebSocketSession): Char? {
        val isPlayerX = state.value.connectedPlayers.any { it == 'X' }
        val player = if(isPlayerX) 'O' else 'X'

        state.update { gameState ->
            if(state.value.connectedPlayers.contains(player)) {
                return null
            }

            if(!playerSockets.containsKey(player)) {
                playerSockets[player] = session
            }

            gameState.copy (
                connectedPlayers = gameState.connectedPlayers + player
            )
        }

        return player
    }

    fun disconnectPlayer(player: Char) {
        playerSockets.remove(player)
        state.update { gameState ->
            gameState.copy(
                connectedPlayers = gameState.connectedPlayers - player
            )
        }
    }

    private suspend fun broadcast(state: GameState) {
        playerSockets.values.forEach { socket ->
            socket.send(
                Json.encodeToString(state)
            )
        }
    }

    fun finishTurn(player: Char, x: Int, y: Int) {
        if(state.value.field[y][x] != null || state.value.winningPlayer != null) {
            return
        }
        if(state.value.playerAtTurn != player) {
            return
        }

        val currentPlayer = state.value.playerAtTurn
        state.update { gameState ->
            val newField = gameState.field.also { field ->
                field[y][x] = currentPlayer
            }

            val isBoardFull = newField.all { row ->
                row.all { cell -> cell != null }
            }
            if(isBoardFull) {
                startNewRoundDelayed()
            }

            gameState.copy(
                playerAtTurn = if(currentPlayer == 'X') 'O' else 'X',
                field = newField,
                isBoardFull = isBoardFull,
                winningPlayer = checkWinningPlayer()?.also {
                    startNewRoundDelayed()
                }
            )

        }
    }

    private fun startNewRoundDelayed() {
        delayGameJob?.cancel()
        delayGameJob = gameScope.launch {
            delay(2000)
            state.update { gameState ->
                gameState.copy(
                    playerAtTurn = 'X',
                    field = GameState.emptyField(),
                    winningPlayer = null,
                    isBoardFull = false
                )
            }
        }
    }

    private fun checkWinningPlayer(): Char? {
        val field = state.value.field

        return if(field[0][0] != null && field[0][0] == field[0][1] && field[0][1] == field[0][2]) {
            field[0][0]
        } else if (field[1][0] != null && field[1][0] == field[1][1] && field[1][1] == field[1][2]) {
            field[1][0]
        } else if (field[2][0] != null && field[2][0] == field[2][1] && field[2][1] == field[2][2]) {
            field[2][0]
        } else if (field[0][0] != null && field[0][0] == field[1][0] && field[1][0] == field[2][0]) {
            field[0][0]
        } else if (field[0][1] != null && field[0][1] == field[1][1] && field[1][1] == field[2][1]) {
            field[0][1]
        } else if (field[0][2] != null && field[0][2] == field[1][2] && field[1][2] == field[2][2]) {
            field[0][2]
        } else if (field[0][0] != null && field[0][0] == field[1][1] && field[1][1] == field[2][2]) {
            field[0][0]
        } else if (field[0][2] != null && field[0][2] == field[1][1] && field[1][1] == field[2][0]) {
            field[0][2]
        } else {
            null
        }
    }
}