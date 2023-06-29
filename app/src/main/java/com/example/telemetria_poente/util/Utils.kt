package com.example.telemetria_poente.util

fun findBoatIdByEmail(data: List<Pair<String, Map<String, Any?>>>, authenticatedEmail: String): String? {
    val boat = data.find { (_, boatInfo) ->
        boatInfo["email"] == authenticatedEmail
    }

    return boat?.second?.get("id")?.toString()
}