package com.codewithfk

import com.codewithfk.configs.FacebookAuthConfig
import com.codewithfk.configs.GoogleAuthConfig
import com.codewithfk.controllers.PaymentController
import com.codewithfk.database.DatabaseFactory
import com.codewithfk.database.migrateDatabase
import com.codewithfk.database.seedDatabase
import com.codewithfk.routs.*
import com.codewithfk.services.FirebaseService
import com.codewithfk.utils.respondError
import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.plugins.callloging.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.websocket.*
import io.ktor.websocket.*
import java.time.Duration

fun main(args: Array<String>) {
    io.ktor.server.netty.EngineMain.main(args)
}

fun Application.module() {
    install(ContentNegotiation) {
        json()
    }
    install(CallLogging)

    // ✅ Khởi tạo Firebase Admin bằng ADC
    FirebaseService.initIfNeeded()

    // Config Authentication
    install(Authentication) {
        jwt {
            realm = "ktor.io"
            verifier(JwtConfig.verifier)
            validate { credential ->
                if (credential.payload.getClaim("userId").asString() != null) {
                    JWTPrincipal(credential.payload)
                } else null
            }
        }
        oauth("google-oauth") {
            client = HttpClient(CIO)
            providerLookup = {
                OAuthServerSettings.OAuth2ServerSettings(
                    name = "google",
                    authorizeUrl = GoogleAuthConfig.authorizeUrl,
                    accessTokenUrl = GoogleAuthConfig.tokenUrl,
                    clientId = GoogleAuthConfig.clientId,
                    clientSecret = GoogleAuthConfig.clientSecret,
                    defaultScopes = listOf("profile", "email")
                )
            }
            urlProvider = { GoogleAuthConfig.redirectUri }
        }
        oauth("facebook-oauth") {
            client = HttpClient(CIO)
            providerLookup = {
                OAuthServerSettings.OAuth2ServerSettings(
                    name = "facebook",
                    authorizeUrl = FacebookAuthConfig.authorizeUrl,
                    accessTokenUrl = FacebookAuthConfig.tokenUrl,
                    clientId = FacebookAuthConfig.clientId,
                    clientSecret = FacebookAuthConfig.clientSecret,
                    defaultScopes = listOf("public_profile", "email")
                )
            }
            urlProvider = { FacebookAuthConfig.redirectUri }
        }
    }

    // ✅ Khởi tạo DB + migrate + seed
    DatabaseFactory.init()
    migrateDatabase()
    seedDatabase()

    install(WebSockets) {
        pingPeriod = Duration.ofSeconds(15)
        timeout = Duration.ofSeconds(15)
        maxFrameSize = Long.MAX_VALUE
        masking = false
    }

    routing {

        get("/") {
            call.respondText("Hello, the server is running!")
        }

        // ✅ Route test server
        get("/health") {
            call.respond(HttpStatusCode.OK, mapOf("status" to "OK"))
        }

        authRoutes()
        categoryRoutes()
        restaurantRoutes()
        menuItemRoutes()
        imageRoutes()
        riderRoutes()

        post("/payments/webhook") {
            try {
                val payload = call.receiveText()
                val signature = call.request.header("Stripe-Signature")
                    ?: throw IllegalArgumentException("No signature header")

                val success = PaymentController.handleWebhookEvent(payload, signature)
                call.respond(HttpStatusCode.OK, mapOf("success" to success))
            } catch (e: Exception) {
                call.respondError(
                    HttpStatusCode.BadRequest,
                    e.message ?: "Webhook processing failed"
                )
            }
        }

        authenticate {
            orderRoutes()
            cartRoutes()
            addressRoutes()
            paymentRoutes()
            notificationRoutes()
            restaurantOwnerRoutes()
        }

        trackingRoutes()
    }
}
