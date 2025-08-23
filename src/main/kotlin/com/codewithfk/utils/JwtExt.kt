package com.codewithfk.utils

import com.auth0.jwt.interfaces.DecodedJWT
import io.ktor.server.auth.jwt.*
import java.util.*

fun JWTPrincipal.safeUserId(): UUID? {
    val idStr = payload.getClaim("userId").asString()
        ?: payload.getClaim("sub").asString()
        ?: payload.getClaim("id").asString()
    return idStr?.let { runCatching { UUID.fromString(it) }.getOrNull() }
}

