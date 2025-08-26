package com.codewithfk.repository

import com.codewithfk.database.MenuItemsTable
import com.codewithfk.model.MenuItem
import org.jetbrains.exposed.sql.ResultRow

fun ResultRow.toMenuItem(): MenuItem {
    return MenuItem(
        id = this[MenuItemsTable.id].toString(),
        restaurantId = this[MenuItemsTable.restaurantId].toString(),
        name = this[MenuItemsTable.name],
        description = this[MenuItemsTable.description],
        price = this[MenuItemsTable.price],
        imageUrl = this[MenuItemsTable.imageUrl],
        arModelUrl = this[MenuItemsTable.arModelUrl],
        createdAt = this[MenuItemsTable.createdAt].toString()
    )
}