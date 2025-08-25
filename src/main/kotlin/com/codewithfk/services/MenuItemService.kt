package com.codewithfk.services


import com.codewithfk.database.MenuItemsTable
import com.codewithfk.model.MenuItem
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.Count
import java.util.*


object MenuItemService {


    fun getMenuItemById(id: UUID): MenuItem? {
        return transaction {
            MenuItemsTable.select { MenuItemsTable.id eq id }
                .map { toMenuItem(it) }
                .singleOrNull()
        }
    }

    fun getMenuItemsFromMostPopulatedRestaurant(): Pair<UUID?, List<MenuItem>> {
        return transaction {
            val mostPopulatedRestaurantId = MenuItemsTable
                .slice(MenuItemsTable.restaurantId, Count(MenuItemsTable.id).alias("itemCount"))
                .selectAll()
                .groupBy(MenuItemsTable.restaurantId)
                .orderBy(Count(MenuItemsTable.id), SortOrder.DESC)
                .limit(1)
                .map { it[MenuItemsTable.restaurantId] }
                .firstOrNull()

            val menuItems = if (mostPopulatedRestaurantId != null) {
                MenuItemsTable.select { MenuItemsTable.restaurantId eq mostPopulatedRestaurantId }
                    .map { toMenuItem(it) }
            } else {
                emptyList()
            }

            Pair(mostPopulatedRestaurantId, menuItems)
        }
    }


    fun getMenuItemsByRestaurant(restaurantId: UUID): List<MenuItem> {
        return transaction {
            MenuItemsTable.select { MenuItemsTable.restaurantId eq restaurantId }
                .map {
                    MenuItem(
                        id = it[MenuItemsTable.id].toString(),
                        restaurantId = it[MenuItemsTable.restaurantId].toString(),
                        name = it[MenuItemsTable.name],
                        description = it[MenuItemsTable.description],
                        price = it[MenuItemsTable.price],
                        imageUrl = it[MenuItemsTable.imageUrl],
                        arModelUrl = it[MenuItemsTable.arModelUrl],
                        createdAt = it[MenuItemsTable.createdAt].toString()
                    )
                }
        }
    }

    fun addMenuItem(menuItem: MenuItem): UUID {
        return transaction {
            MenuItemsTable.insertAndGetId { // Dùng insertAndGetId cho an toàn
                it[restaurantId] = UUID.fromString(menuItem.restaurantId) // Bỏ this
                it[name] = menuItem.name
                it[description] = menuItem.description
                it[price] = menuItem.price
                it[imageUrl] = menuItem.imageUrl
                it[arModelUrl] = menuItem.arModelUrl
            }.value // Thêm .value để lấy UUID thật
        }
    }

    fun updateMenuItem(itemId: UUID, updatedFields: Map<String, Any?>): Boolean {
        return transaction {
            MenuItemsTable.update({ MenuItemsTable.id eq itemId }) {row->
                updatedFields["name"]?.let { row[MenuItemsTable.name] = it as String }
                updatedFields["description"]?.let { row[MenuItemsTable.description] = it as String }
                updatedFields["price"]?.let { row[MenuItemsTable.price] = it as Double }
                updatedFields["imageUrl"]?.let { row[MenuItemsTable.imageUrl] = it as String }
                updatedFields["arModelUrl"]?.let { row[MenuItemsTable.arModelUrl] = it as String }
            } > 0
        }
    }

    fun deleteMenuItem(itemId: UUID): Boolean {
        return transaction {
            MenuItemsTable.deleteWhere { MenuItemsTable.id eq itemId } > 0
        }
    }

    private fun toMenuItem(row: ResultRow): MenuItem {
        return MenuItem(
            id = row[MenuItemsTable.id].toString(),
            restaurantId = row[MenuItemsTable.restaurantId].toString(),
            name = row[MenuItemsTable.name],
            description = row[MenuItemsTable.description],
            price = row[MenuItemsTable.price],
            imageUrl = row[MenuItemsTable.imageUrl],
            arModelUrl = row[MenuItemsTable.arModelUrl],
            createdAt = row[MenuItemsTable.createdAt].toString()
        )
    }
}