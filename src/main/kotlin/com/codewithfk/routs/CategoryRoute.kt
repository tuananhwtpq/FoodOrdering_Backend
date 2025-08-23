package com.codewithfk.routs

import com.codewithfk.services.CategoryService
import com.codewithfk.services.RestaurantService
import com.codewithfk.utils.respondError
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.response.*
import io.ktor.server.request.*
import io.ktor.server.routing.*
import java.util.UUID

fun Route.categoryRoutes() {
    route("/categories") {

        // --- CÁC ROUTE KHÔNG CÓ THAM SỐ PATH ---

        /**
         * Get all categories (public).
         */
        get {
            val categories = CategoryService.getAllCategories()
            call.respond(mapOf("data" to categories))
        }

        /**
         * Add a new category (admin-only).
         */
        authenticate {
            post {
                val params = call.receive<Map<String, String>>()
                val name = params["name"] ?: return@post call.respondError(
                    "Name is required", status = HttpStatusCode.BadRequest
                )
                val imageUrl = params["imageUrl"] ?: return@post call.respondError(
                    "Image URL is required", status = HttpStatusCode.BadRequest
                )

                val categoryId = CategoryService.addCategory(name, imageUrl)

                call.respond(mapOf("id" to categoryId.toString(), "message" to "Category added successfully"))
            }
        }

        // --- NHÓM CÁC ROUTE CÓ CHUNG THAM SỐ / {id} ---
        route("/{id}") {

            /**
             * Get all restaurants for a specific category (public).
             * Endpoint: GET /categories/{id}/restaurants
             */
            get("/restaurants") {
                val categoryId = call.parameters["id"] ?: return@get call.respondError(
                    "Category ID is required",
                    status = HttpStatusCode.BadRequest
                )
                val restaurants = RestaurantService.getRestaurantsByCategoryId(UUID.fromString(categoryId))
                call.respond(mapOf("data" to restaurants))
            }

            /**
             * Delete a category by id (public for testing).
             * Endpoint: DELETE /categories/{id}
             */
            delete {
                val categoryId = call.parameters["id"] ?: return@delete call.respondError(
                    "Category ID is required",
                    status = HttpStatusCode.BadRequest
                )
                val success = CategoryService.deleteCategory(UUID.fromString(categoryId))
                if (success) {
                    call.respond(mapOf("message" to "Category deleted successfully"))
                } else {
                    call.respondError("Category not found", HttpStatusCode.NotFound)
                }
            }
        }
    }
}