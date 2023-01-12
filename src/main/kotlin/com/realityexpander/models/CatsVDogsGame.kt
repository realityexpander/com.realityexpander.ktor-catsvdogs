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

    private val playerSocketsMap = ConcurrentHashMap<Char, WebSocketSession>()
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

            if(!playerSocketsMap.containsKey(player)) {
                playerSocketsMap[player] = session
            }

            gameState.copy (
                connectedPlayers = gameState.connectedPlayers + player
            )
        }

        return player
    }

    fun disconnectPlayer(player: Char) {
        playerSocketsMap.remove(player)
        state.update { gameState ->
            gameState.copy(
                connectedPlayers = gameState.connectedPlayers - player
            )
        }
    }

    private suspend fun broadcast(state: GameState) {
        playerSocketsMap.values.forEach { socket ->
            socket.send(
                Json.encodeToString(state)
            )
        }
    }

    fun processTurn(player: Char, x: Int, y: Int) {
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
                winningPlayer = checkWinningPlayer4in5()?.also {
                    startNewRoundDelayed()
                }
            )

        }
    }

    private fun startNewRoundDelayed() {
        delayGameJob?.cancel()
        delayGameJob = gameScope.launch {
            delay(5000)
            state.update { gameState ->
                gameState.copy(
//                    playerAtTurn = if(gameState.playerAtTurn == 'O') 'X' else 'O',
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

    private fun checkWinningPlayer4x4(): Char? {
        val field = state.value.field

        // check for 4x4

        // check rows
        for (i in 0..3) {
            if (field[i][0] != null
                && field[i][0] == field[i][1]
                && field[i][1] == field[i][2]
                && field[i][2] == field[i][3]
            ) {
                return field[i][0]
            }
        }

        // check columns
        for (i in 0..3) {
            if (field[0][i] != null
                && field[0][i] == field[1][i]
                && field[1][i] == field[2][i]
                && field[2][i] == field[3][i]
            ) {
                return field[0][i]
            }
        }

        // check diagonals
        if (field[0][0] != null
            && field[0][0] == field[1][1]
            && field[1][1] == field[2][2]
            && field[2][2] == field[3][3]
        ) {
            return field[0][0]
        }
        if (field[0][3] != null
            && field[0][3] == field[1][2]
            && field[1][2] == field[2][1]
            && field[2][1] == field[3][0]
        ) {
            return field[0][3]
        }

        return null
    }

    private fun checkWinningPlayer3in4(): Char? {
        val field = state.value.field

        // check for 3 in 4

        // check rows
        for(y in 0..3) {
            var xCount = 0
            var oCount = 0
            for (x in 0..3) {
                val char = field[y][x]
                if (char != null) {
                    if (char == 'X') {
                        xCount++
                    } else {
                        oCount++
                    }
                }

                if (xCount >= 3) {
                    return 'X'
                }

                if (oCount >= 3) {
                    return 'O'
                }
            }
        }

        // check columns
        for(x in 0..3) {
            var xCount = 0
            var oCount = 0
            for (y in 0..3) {
                val char = field[y][x]
                if (char != null) {
                    if (char == 'X') {
                        xCount++
                    } else {
                        oCount++
                    }
                }

                if (xCount >= 3) {
                    return 'X'
                }

                if (oCount >= 3) {
                    return 'O'
                }
            }
        }

        // check diagonals (L->R)
        for(y in -1..1) {
            var xCount = 0
            var oCount = 0
            for (i in 0..3) {
                if(i+y < 0 || i+y > 3) {
                    continue
                }
                val char = field[i+y][i]

                if (char != null) {
                    if (char == 'X') {
                        xCount++
                    } else {
                        oCount++
                    }
                }

                if (xCount >= 3) {
                    return 'X'
                }

                if (oCount >= 3) {
                    return 'O'
                }
            }
        }

        // check diagonals (R->L)
        for(y in -1..1) {
            var xCount = 0
            var oCount = 0
            for (i in 0..3) {
                if(i+y < 0 || i+y > 3) {
                    continue
                }
                val char = field[i+y][3 - i]

                if (char != null) {
                    if (char == 'X') {
                        xCount++
                    } else {
                        oCount++
                    }
                }

                if (xCount >= 3) {
                    return 'X'
                }

                if (oCount >= 3) {
                    return 'O'
                }
            }
        }

        return null
    }

    private fun checkWinningPlayer4in5(): Char? {
        val field = state.value.field

        // check for 4 in 5

        // check rows
        for(y in 0..4) {
            var xCount = 0
            var oCount = 0
            for (x in 0..4) {
                val char = field[y][x]
                if (char != null) {
                    if (char == 'X') {
                        xCount++
                    } else {
                        oCount++
                    }
                }

                if (xCount >= 4) {
                    return 'X'
                }

                if (oCount >= 4) {
                    return 'O'
                }
            }
        }

        // check columns
        for(x in 0..4) {
            var xCount = 0
            var oCount = 0
            for (y in 0..4) {
                val char = field[y][x]
                if (char != null) {
                    if (char == 'X') {
                        xCount++
                    } else {
                        oCount++
                    }
                }

                if (xCount >= 4) {
                    return 'X'
                }

                if (oCount >= 4) {
                    return 'O'
                }
            }
        }

        // check diagonals (L->R)
        for(y in -2..2) {
            var xCount = 0
            var oCount = 0
            for (i in 0..4) {
                if(i+y < 0 || i+y > 4) {
                    continue
                }
                val char = field[i+y][i]

                if (char != null) {
                    if (char == 'X') {
                        xCount++
                    } else {
                        oCount++
                    }
                }

                if (xCount >= 4) {
                    return 'X'
                }

                if (oCount >= 4) {
                    return 'O'
                }
            }
        }

        // check diagonals (R->L)
        for(y in -2..2) {
            var xCount = 0
            var oCount = 0
            for (i in 0..4) {
                if (i + y < 0 || i + y > 4) {
                    continue
                }
                val char = field[i + y][4 - i]

                if (char != null) {
                    if (char == 'X') {
                        xCount++
                    } else {
                        oCount++
                    }
                }

                if (xCount >= 4) {
                    return 'X'
                }

                if (oCount >= 4) {
                    return 'O'
                }
            }
        }

        return null
    }
}