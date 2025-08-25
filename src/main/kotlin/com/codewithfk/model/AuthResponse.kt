package com.codewithfk.model

import kotlinx.serialization.Serializable

@Serializable
data class AuthResponse(
    val token: String,
    val role: String,
    val userId: String,
    val email: String,
    val username: String,
    val isProfileComplete: Boolean
)
