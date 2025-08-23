package com.codewithfk.services

import com.codewithfk.database.CategoriesTable
import com.codewithfk.database.MenuItemsTable
import com.codewithfk.database.RestaurantsTable
import com.codewithfk.model.Category
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.inList
import org.jetbrains.exposed.sql.transactions.transaction
import java.util.UUID

object CategoryService {

    /**
     * Fetch all categories.
     */
    fun getAllCategories(): List<Category> {
        return transaction {
            CategoriesTable.selectAll()
                .map {
                    Category(
                        id = it[CategoriesTable.id].toString(),
                        name = it[CategoriesTable.name],
                        imageUrl = it[CategoriesTable.imageUrl].toString(),
                        createdAt = it[CategoriesTable.createdAt].toString()
                    )
                }
        }
    }

    /**
     * Add a new category (admin-only functionality).
     */
    fun addCategory(name: String, imageUrl: String): UUID {
        return transaction {
            CategoriesTable.insert {
                it[this.name] = name
                it[this.imageUrl] = imageUrl
            } get CategoriesTable.id
        }
    }

    //Xóa 1 danh mục theo ID
    fun deleteCategory(id: UUID): Boolean {
        return transaction {
            val restaurantIds = RestaurantsTable
                .select { RestaurantsTable.categoryId eq id }
                .map { it[RestaurantsTable.id] }

            if (restaurantIds.isNotEmpty()) {
                MenuItemsTable.deleteWhere { MenuItemsTable.restaurantId inList restaurantIds }

                RestaurantsTable.deleteWhere { RestaurantsTable.categoryId eq id }
            }

            val deletedRows = CategoriesTable.deleteWhere { CategoriesTable.id eq id }

            deletedRows > 0
        }
    }

}