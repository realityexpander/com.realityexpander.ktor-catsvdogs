package com.realityexpander

import com.realityexpander.models.CatsVDogsGame
import com.realityexpander.models.MakeTurn
import com.realityexpander.models.Player2
import io.ktor.server.routing.*
import io.ktor.server.websocket.*
import io.ktor.websocket.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.consumeEach
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.util.*
import java.util.concurrent.CancellationException

@Serializable
data class Player(val playerName: String, val id: String)

@OptIn(ExperimentalCoroutinesApi::class)
fun Route.socket(game: CatsVDogsGame) {
    route("/play") {

        webSocket("/socket/{id}") {
            val userId = call.parameters["id"] ?: return@webSocket
            println("connect Player: $userId")

            val player = game.connectPlayer(this, userId = userId)

            if(player == null) {
                close(CloseReason(CloseReason.Codes.VIOLATED_POLICY, "Too many players"))
                return@webSocket
            }

            var pongMissed = 0
            var shouldDisconnectPlayer = true

            try {

                // Send the player their "X" or "O" status
                outgoing.send(Frame.Text(
                    Json.encodeToString(
                        Player(
                            playerName = "$player",
                            id = UUID.randomUUID().toString()
                        )
                    )
                ))

                // Setup ping/pong to be sure client is still there
                launch {
                    while (true) {
                        outgoing.send(Frame.Text("ping"))

                        pongMissed++
                        delay(500)
                        if(pongMissed > 10) {
                            println("Too many pings missed: $userId")
                            cancel("Too many pings missed")
                            shouldDisconnectPlayer = false
                            game.disconnectPlayer(player, Player2(userId, player))
                            close(CloseReason(CloseReason.Codes.VIOLATED_POLICY, "Too many pings missed"))
                            throw CancellationException("Too many pings missed")
                            //return@launch
                        }
                    }
                }

                // Consume events from the client
                incoming.consumeEach { frame ->
                    println("Received: $frame")
                    if(frame is Frame.Text) {
                        val frameText = frame.readText()
                        println("Received: $frameText")

                        // Reset pongMissed if we get a pong
                        if(frameText == "pong") {
                            pongMissed = 0
                            return@consumeEach
                        }

                        val action = extractAction(frame.readText())
                        println("Received: $frameText, action: $action")

                        game.processTurn(player, action.x, action.y)
                    } else {
                        println("Received: $frame")
                    }
                }

                println("End of socket: $userId, shouldDisconnect: $shouldDisconnectPlayer")
                if(shouldDisconnectPlayer) {
                    println("Player disconnected: $userId")
                    game.disconnectPlayer(player, Player2(userId, player))
                }

            } catch(e: CancellationException){
                println("Player Cancellation Exception: $userId")
                game.disconnectPlayer(player, Player2(userId, player))
                throw e
            }
            catch (e: Exception) {
                e.printStackTrace()
                game.disconnectPlayer(player, Player2(userId, player))
            } finally {
//                println("Player disconnected: $userId")
//                game.disconnectPlayer(player, Player2(userId, player))
            }
        }
    }

    // Returns pings from client
    route("/echo") {
        webSocket {
            try {
                incoming.consumeEach { frame ->
                    //println("/echo Received: $frame")
                    if (frame is Frame.Text) {
                        //println("/echo Received: ${frame.readText()} $frame")
                        outgoing.send(Frame.Text("You said: ${frame.readText()}"))
                    }
                }
            } catch(e: CancellationException){
                println("echo Cancellation Exception")
                throw e
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
}

private fun extractAction(message: String): MakeTurn {
    // make turn#{json}
    val type = message.substringBefore("#")
    val payload = message.substringAfter("#")

    return if (type == "make_turn") {
        Json.decodeFromString(payload)
    } else {
        MakeTurn(-1, -1)
        throw Exception("Unknown action type: $type")
    }

}