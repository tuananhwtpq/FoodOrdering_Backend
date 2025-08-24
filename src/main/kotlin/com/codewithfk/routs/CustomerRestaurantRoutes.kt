package com.codewithfk.routs

import com.codewithfk.services.MenuItemService
import com.codewithfk.services.RestaurantService
import com.codewithfk.utils.respondError
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.route
import java.util.UUID

fun Route.customerRestaurantRoutes() {
    route("/restaurants") {

        get("/{id}") {
            val id = call.parameters["id"] ?: return@get call.respondError(
                "Restaurant ID is required.", HttpStatusCode.BadRequest
            )

            try {
                val restaurant = RestaurantService.getRestaurantById(UUID.fromString(id))
                    ?: return@get call.respondError("Restaurant not found.", HttpStatusCode.NotFound)

                call.respond(HttpStatusCode.OK, mapOf("data" to restaurant))

            } catch (e: IllegalArgumentException) {
                call.respondError("Invalid Restaurant ID format.", HttpStatusCode.BadRequest)
            }
        }


        get("/{id}/menu") {
            val restaurantId = call.parameters["id"] ?: return@get call.respondError(
                "Restaurant ID is required.", HttpStatusCode.BadRequest
            )

            try {
                val menuItems = MenuItemService.getMenuItemsByRestaurant(UUID.fromString(restaurantId))

                call.respond(HttpStatusCode.OK, mapOf("data" to menuItems))

            } catch (e: IllegalArgumentException) {
                call.respondError("Invalid Restaurant ID format.", HttpStatusCode.BadRequest)
            }
        }
    }
}