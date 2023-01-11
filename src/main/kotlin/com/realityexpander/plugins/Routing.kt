package com.realityexpander.plugins

import com.realityexpander.models.CatsVDogsGame
import com.realityexpander.socket
import io.ktor.server.application.*
import io.ktor.server.routing.*

fun Application.configureRouting(game: CatsVDogsGame) {

    routing {
        socket(game)
    }


    // Not needed but left in for reference
//    routing {
//        get("/") {
//            call.respondText("Hello World!")
//        }
//    }
}
