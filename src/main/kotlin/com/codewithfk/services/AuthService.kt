package com.codewithfk.services


import at.favre.lib.crypto.bcrypt.BCrypt
import com.codewithfk.JwtConfig
import com.codewithfk.database.UsersTable
import com.codewithfk.model.AuthProvider
import com.codewithfk.model.AuthResponse
import com.codewithfk.model.UserRole
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.cio.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.transaction
import java.util.UUID

object AuthService {
    private val httpClient = HttpClient(CIO)

    fun register(name: String, email: String, plainTextPassword: String, role: String): AuthResponse? { // Đổi tên biến cho rõ nghĩa
        return transaction {
            if (UsersTable.select { UsersTable.email eq email }.count() > 0) {
                return@transaction null // Email đã tồn tại
            }

            val hashedPassword = BCrypt.withDefaults().hashToString(12, plainTextPassword.toCharArray())

            val userId = UUID.randomUUID()
            UsersTable.insert {
                it[id] = userId
                it[this.name] = name
                it[this.email] = email
                it[this.passwordHash] = hashedPassword
                it[this.role] = role
                it[this.authProvider] = "email"
            }
            val address = AddressService.getAddressesByUser(userId)
            if (address.isEmpty()) {
                AddressService.createDefaultAddress(userId)
            }
            val token = JwtConfig.generateToken(userId.toString())

            AuthResponse(token = token, role = role, userId =  userId.toString(), email = email, username = name)
        }
    }

    fun getUserEmailFromID(userId: UUID): String? {
        return transaction {
            val user = UsersTable.select { UsersTable.id eq userId }.singleOrNull()
            user?.get(UsersTable.email)
        }
    }
    private fun isBcryptHash(value: String): Boolean {
        // BCrypt phổ biến có prefix $2a$, $2b$, $2y$
        return value.startsWith("\$2a$") || value.startsWith("\$2b$") || value.startsWith("\$2y$")
    }
    private fun hashPassword(plain: String): String {
        return BCrypt.withDefaults().hashToString(12, plain.toCharArray())
    }
    fun login(
        email: String,
        plainTextPassword: String,
        role: String,
        rehashLegacy: Boolean = true // true = re-hash tài khoản cũ (plaintext) ngay lần đăng nhập đầu
    ): AuthResponse? {
        return transaction {
            val userRow = UsersTable.select {
                (UsersTable.email eq email) and (UsersTable.role.lowerCase() eq role.lowercase())
            }.singleOrNull() ?: return@transaction null

            val stored = userRow[UsersTable.passwordHash]
            val storedLooksLikeBcrypt = isBcryptHash(stored.toString())

            // 1) Nếu là bcrypt -> verify bằng BCrypt
            val bcryptVerified = if (storedLooksLikeBcrypt) {
                runCatching {
                    BCrypt.verifyer().verify(plainTextPassword.toCharArray(), stored).verified
                }.getOrDefault(false)
            } else false

            when {
                // Đúng mật khẩu theo bcrypt
                bcryptVerified -> buildAuthResponse(userRow)

                // 2) Không phải bcrypt và plaintext trùng -> cho đăng nhập
                !storedLooksLikeBcrypt && stored == plainTextPassword -> {
                    if (rehashLegacy) {
                        // Chuẩn hoá dữ liệu: cập nhật ngay thành bcrypt để các lần sau verify chuẩn
                        val newHash = hashPassword(plainTextPassword)
                        UsersTable.update({ UsersTable.id eq userRow[UsersTable.id] }) {
                            it[passwordHash] = newHash
                        }
                    }
                    buildAuthResponse(userRow)
                }

                // Sai mật khẩu
                else -> null
            }
        }
    }

    /* -------------------- Helper dựng AuthResponse (THÊM MỚI) -------------------- */
    private fun buildAuthResponse(userRow: ResultRow): AuthResponse {
        val userId = userRow[UsersTable.id]
        val userRole = userRow[UsersTable.role]
        val userName = userRow[UsersTable.name]
        val emailFromDb = userRow[UsersTable.email]
        val token = JwtConfig.generateToken(userId.toString()) // Có thể thêm claim role/exp nếu cần

        return AuthResponse(
            token = token,
            role = userRole,
            userId = userId.toString(),
            email = emailFromDb,
            username = userName
        )
    }

    // Google OAuth User Info
    /**
     * Validate Google ID Token and get user information.
     */
    suspend fun validateGoogleToken(idToken: String): Map<String, String>? {
        val response: HttpResponse = httpClient.get("https://oauth2.googleapis.com/tokeninfo") {
            parameter("id_token", idToken)
        }
        val responseBody = response.bodyAsText() // Read as plain text first
        println("Response Body: $responseBody") // Debug response

        // Parse as JsonObject
        val jsonObject: JsonObject = Json.parseToJsonElement(responseBody).jsonObject

        return if (response.status == HttpStatusCode.OK) {
            val userInfo = jsonObject
            mapOf(
                "email" to userInfo["email"]?.jsonPrimitive?.content.orEmpty(),
                "name" to userInfo["name"]?.jsonPrimitive?.content.orEmpty()
            )
        } else {
            null
        }
    }

    /**
     * Validate Facebook Access Token and get user information.
     */
    suspend fun validateFacebookToken(accessToken: String): Map<String, String>? {
        val response: HttpResponse = httpClient.get("https://graph.facebook.com/me") {
            parameter("fields", "id,name,email")
            parameter("access_token", accessToken)
        }
        val responseBody = response.bodyAsText() // Read as plain text first
        println("Response Body: $responseBody") // Debug response

        // Parse as JsonObject
        val jsonObject: JsonObject = Json.parseToJsonElement(responseBody).jsonObject


        return if (response.status == HttpStatusCode.OK) {
            val userInfo = jsonObject
            mapOf(
                "email" to userInfo["email"]?.jsonPrimitive?.content.orEmpty(),
                "name" to userInfo["name"]?.jsonPrimitive?.content.orEmpty()
            )
        } else {
            null
        }
    }

    /**
     * Handle user registration or login based on OAuth provider.
     */
    fun oauthLoginOrRegister(email: String, name: String, provider: String, userType: String): String {
        return transaction {
            val user = UsersTable.select { UsersTable.email eq email }.singleOrNull()

            if (user == null) {
                // Register a new user
                val userId = UUID.randomUUID()
                UsersTable.insert {
                    it[id] = userId
                    it[this.email] = email
                    it[this.name] = name
                    it[this.authProvider] = provider
                    it[this.role] = userType
                }
                val address = AddressService.getAddressesByUser(userId)
                if (address.isEmpty()) {
                    AddressService.createDefaultAddress(userId)
                }
                JwtConfig.generateToken(userId.toString())
            } else {
                // Generate token for existing user
                val userId = user[UsersTable.id]
                val address = AddressService.getAddressesByUser(userId)
                if (address.isEmpty()) {
                    AddressService.createDefaultAddress(userId)
                }
                JwtConfig.generateToken(userId.toString())
            }
        }
    }

    fun updateFcmToken(userId: UUID, token: String) {
        transaction {
            UsersTable.update({ UsersTable.id eq userId }) {
                it[fcmToken] = token
            }
        }
    }

}