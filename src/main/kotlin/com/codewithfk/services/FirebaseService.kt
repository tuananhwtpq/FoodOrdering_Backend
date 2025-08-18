package com.codewithfk.services

import com.google.auth.oauth2.GoogleCredentials
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.messaging.FirebaseMessaging
import com.google.firebase.messaging.Message
import com.google.firebase.messaging.Notification
import org.slf4j.LoggerFactory
import java.io.FileInputStream

object FirebaseService {
    private val logger = LoggerFactory.getLogger(FirebaseService::class.java)
    @Volatile private var initialized = false

    fun initIfNeeded() {
        if (initialized) return
        synchronized(this) {
            if (initialized) return
            try {
                val creds = GoogleCredentials.getApplicationDefault()
                val options = FirebaseOptions.builder()
                    .setCredentials(creds)
                    .build()
                FirebaseApp.initializeApp(options)
                initialized = true
                logger.info("✅ Firebase Admin initialized using Application Default Credentials.")
            } catch (e: Exception) {
                logger.error("❌ Firebase Admin initialization failed: ${e.message}", e)
                throw e
            }
        }
    }

    fun sendNotification(
        token: String,
        title: String,
        body: String,
        data: Map<String, String> = emptyMap()
    ) {
        try {
            val message = Message.builder()
                .setToken(token)
                .setNotification(
                    Notification.builder()
                        .setTitle(title)
                        .setBody(body)
                        .build()
                )
                .putAllData(data)
                .build()

            val response = FirebaseMessaging.getInstance().send(message)
            println("Successfully sent message: $response")
        } catch (e: Exception) {
            println("Error sending Firebase notification: ${e.message}")
        }
    }
} 