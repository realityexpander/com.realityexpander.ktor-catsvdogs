package com.realityexpander.models

import kotlinx.serialization.Serializable

@Serializable
data class Player2(
    val userId: String,
    val playerCode: Char
)