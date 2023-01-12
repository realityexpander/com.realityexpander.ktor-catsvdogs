package com.realityexpander.plugins

import io.ktor.server.application.*
import io.ktor.server.routing.*
import io.ktor.server.websocket.*
import io.ktor.websocket.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Duration

fun Application.configureSockets(
    clientFlow: MutableStateFlow<Map<String, WebSocketServerSession>>
) {
    install(WebSockets) {
        pingPeriod = Duration.ofSeconds(15)
        timeout = Duration.ofSeconds(15)
        maxFrameSize = Long.MAX_VALUE
        masking = false
    }

    val clients = mutableMapOf<String, WebSocketServerSession>()

    // Not needed but left in for reference - id is the user's id
    routing {
        webSocket("/ws-test/{id}") { // websocketSession

            if(clients.size >= 2) {
                close(CloseReason(CloseReason.Codes.VIOLATED_POLICY, "Too many players"))
                return@webSocket
            }

            val id = call.parameters["id"] ?: return@webSocket
            if(clients.containsKey(id)) {
                close(CloseReason(CloseReason.Codes.CANNOT_ACCEPT, "Player already exists"))
                return@webSocket
            }

            clients[id] = this
            clientFlow.update { it + (id to this) }

            // Start ping for this client
            println("Websocket connected: $id")
            val pingJob = CoroutineScope(coroutineContext).launch {
                while (true) {
                    delay(1000)
                    val msg = "Websocket ping: $id, ${System.currentTimeMillis().toString().takeLast(5)}"
                    outgoing.send(Frame.Text(msg))
                    //println(msg)
                }
            }

            // Listen for messages
            for (frame in incoming) {
                if (frame is Frame.Text) {
                    val text = frame.readText()
                    outgoing.send(Frame.Text("YOU SAID: $text"))

                    if(text.equals("count", ignoreCase = true)) {
                        for (i in 1..10) {
                            delay(100)
                            outgoing.send(Frame.Text("COUNT: $i"))
                        }
                    }

                    if (text.equals("bye", ignoreCase = true)) {
                        close(CloseReason(CloseReason.Codes.NORMAL, "Client said BYE"))
                    }

                    if(text.equals("bc", ignoreCase = true)) {
                        clients.forEach { client ->
                            println("Sending broadcast to ${client.key}")
                            CoroutineScope(coroutineContext).launch {
                                client.value.outgoing.send(Frame.Text("Broadcast Message - ${System.currentTimeMillis()}"))
                            }
                        }
                    }
                }
            }

            // Note: When execution reaches here, the websocket has been closed.
            clients.remove(id)
            clientFlow.update { it - id }
            //pingJob.cancel() // actually not needed since the coroutine context is cancelled.
            println("Websocket disconnected: $id")
            println("Close reason: ${closeReason.await()}")

        }
    }
}
