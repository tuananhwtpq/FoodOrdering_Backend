package com.codewithfk.routs

import com.codewithfk.services.RestaurantService
import com.codewithfk.utils.respondError
import com.codewithfk.utils.safeUserId
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

import java.util.*

fun Route.restaurantRoutes() {
    route("/restaurants") {

        /**
         * Add a new restaurant (admin/owner-only).
         */
        authenticate {
            post {
                val params = call.receive<Map<String, String>>()

                val ownerId = call.principal<JWTPrincipal>()?.payload?.getClaim("userId")?.asString()
                    ?: return@post call.respondError("Không được phép.", HttpStatusCode.Unauthorized)

                val name = params["name"]
                    ?: return@post call.respondError("Tên nhà hàng là bắt buộc.", HttpStatusCode.BadRequest)
                val address = params["address"]
                    ?: return@post call.respondError("Địa chỉ nhà hàng là bắt buộc.", HttpStatusCode.BadRequest)
                val latitude = params["latitude"]?.toDoubleOrNull()
                    ?: return@post call.respondError("Vĩ độ là bắt buộc.", HttpStatusCode.BadRequest)
                val longitude = params["longitude"]?.toDoubleOrNull()
                    ?: return@post call.respondError("Kinh độ là bắt buộc.", HttpStatusCode.BadRequest)
                val categoryId = params["categoryId"]
                    ?: return@post call.respondError("ID danh mục hợp lệ là bắt buộc.", HttpStatusCode.BadRequest)

                val imageUrl = params["imageUrl"]

                val restaurantId = RestaurantService.addRestaurant(
                    ownerId = UUID.fromString(ownerId),
                    name = name,
                    address = address,
                    latitude = latitude,
                    longitude = longitude,
                    categoryId = UUID.fromString(categoryId),
                    imageUrl = imageUrl
                )

                call.respond(
                    mapOf(
                        "id" to restaurantId.toString(),
                        "message" to "Thêm nhà hàng thành công"
                    )
                )
            }


            get("/owner/me") {
                val principal = call.principal<JWTPrincipal>()
                    ?: return@get call.respond(io.ktor.http.HttpStatusCode.Unauthorized, mapOf("error" to "Unauthorized"))

                val ownerUUID = principal.safeUserId()
                    ?: return@get call.respond(io.ktor.http.HttpStatusCode.Unauthorized, mapOf("error" to "Missing userId claim"))

                try {
                    application.environment.log.info("GET /restaurants/owner/me ownerId=$ownerUUID")
                    val restaurants = RestaurantService.getRestaurantsByOwnerId(ownerUUID)
                    call.respond(io.ktor.http.HttpStatusCode.OK, mapOf("data" to restaurants))
                } catch (e: Exception) {
                    application.environment.log.error("owner/me failed for $ownerUUID", e)
                    call.respond(io.ktor.http.HttpStatusCode.InternalServerError, mapOf("error" to "read restaurants failed"))
                }
            }
        }

        /**
         * Fetch nearby restaurants.
         */
        get {
            val lat = call.request.queryParameters["lat"]?.toDoubleOrNull()
            val lon = call.request.queryParameters["lon"]?.toDoubleOrNull()
            val categoryId: String? = call.request.queryParameters["categoryId"]

            if (lat == null || lon == null) {
                call.respondError("Latitude and longitude are required.", HttpStatusCode.BadRequest)
                return@get
            }
            var uuid: UUID? = null
            categoryId?.let { uuid = UUID.fromString(it) }
            val restaurants = RestaurantService.getNearbyRestaurants(lat, lon, uuid)
            call.respond(HttpStatusCode.OK, mapOf("data" to restaurants))
        }

        /**
         * Get details of a specific restaurant.
         */
        get("/{id}") {
            val id = call.parameters["id"] ?: return@get call.respondError(
                "Restaurant ID is required.", HttpStatusCode.BadRequest
            )
            val restaurant = RestaurantService.getRestaurantById(UUID.fromString(id))
                ?: return@get call.respondError("Restaurant not found.", HttpStatusCode.NotFound)

            call.respond(HttpStatusCode.OK, mapOf("data" to restaurant))
        }
    }
}
