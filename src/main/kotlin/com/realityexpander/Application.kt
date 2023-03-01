package com.realityexpander

import com.realityexpander.models.CatsVDogsGame
import com.realityexpander.plugins.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.websocket.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

// Built from: https://start.ktor.io/#/final?name=ktor-catsvdogs&website=realityexpander.com&artifact=com.realityexpander.ktor-catsvdogs&kotlinVersion=1.8.0&ktorVersion=2.1.3&buildSystem=GRADLE_KTS&engine=NETTY&configurationIn=HOCON&addSampleCode=true&plugins=routing%2Cktor-websockets%2Ccontent-negotiation%2Ckotlinx-serialization%2Ccall-logging

fun main(args: Array<String>): Unit =
    io.ktor.server.netty.EngineMain.main(args)

@Suppress("unused") // application.conf references the main function. This annotation prevents the IDE from marking it as unused.
fun Application.module() {

    install(WebSockets) {
        pingPeriodMillis = 0 //15_000 // we impl our own ping. 0 disables the default ping.
        timeoutMillis = 0 //15_000  // we impl our own ping. 0 disables the default ping.
//        pingPeriod = Duration.ofSeconds(0) //Duration.ofSeconds(15)
//        timeout = Duration.ofSeconds(15)
        maxFrameSize = Long.MAX_VALUE
        masking = false
    }
    val game = CatsVDogsGame()


    ///////////////// EXPERIMENTS WITH POSTMAN //////////////////

    // create flow of clients sockets
    val socketClientsFlow =
        MutableStateFlow<Map<String, WebSocketServerSession>>(mapOf())

    //configureSockets(socketClientsFlow)
    configureSerialization()
    configureMonitoring()
    configureRouting(game)

    launch {
        socketClientsFlow.collect { socketClientMap ->
            println("socketClientMap: $socketClientMap")
        }
    }
}
