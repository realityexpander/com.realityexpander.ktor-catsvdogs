package com.realityexpander

import com.realityexpander.models.CatsVDogsGame
import com.realityexpander.models.MakeTurn
import io.ktor.server.routing.*
import io.ktor.server.websocket.*
import io.ktor.websocket.*
import kotlinx.coroutines.channels.consumeEach
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.awt.SystemColor.text
import java.util.*

@Serializable
data class Player(val playerName: String, val id: String)

fun Route.socket(game: CatsVDogsGame) {
    route("/play") {
        webSocket("/socket") {
            val player = game.connectPlayer(this)

            if(player == null) {
                close(CloseReason(CloseReason.Codes.VIOLATED_POLICY, "Too many players"))
                return@webSocket
            }

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

                // Consume events from the client
                incoming.consumeEach { frame ->
                    if(frame is Frame.Text) {
                        val action = extractAction(frame.readText())
                        println("Received: $text, action: $action")

                        game.finishTurn(player, action.x, action.y)
                    }
                }

            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                game.disconnectPlayer(player)
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